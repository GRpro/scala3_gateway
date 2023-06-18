package com.gr.gateway

import cats.effect.kernel.{Fiber, GenConcurrent}
import cats.effect.std.{AtomicCell, Queue, Semaphore}
import cats.effect.{Async, Deferred, Sync}
import cats.implicits.*
import cats.syntax.all.*
import cats.{Applicative, Functor, Monad, Traverse}
import com.gr.gateway.service.Fortune.FortuneError
import com.gr.gateway.config.{EndpointConfig, GatewayConfig}
import com.gr.gateway.{GenConcurrentThrowable}
import com.gr.gateway.service.Fortune
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.ExecutionContext


trait RequestScheduler[F[_] : Monad : Async : GenConcurrentThrowable]:
  def run(): F[Fiber[F, Throwable, Unit]]

object RequestScheduler:

  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  def impl[F[_] : Monad : Async : GenConcurrentThrowable](config: GatewayConfig,
                                                          fortuneService: Fortune[F],
                                                          activeRequestsStats: AtomicCell[F, Int],
                                                          requestQueue: Queue[F, Deferred[F, FortuneError Either Fortune.FortuneResponse]]): RequestScheduler[F] =
    new RequestScheduler[F]:

      // Represents single permit to execute a request for a particular endpoint
      private case class Permit(connection: EndpointConfig)

      override def run(): F[Fiber[F, Throwable, Unit]] =
        for {
          endpoints <- Queue.unbounded[F, Permit]

          // initial request permits will go in round-robin order
          _ <- (1 to config.maxParallelRequestsPerConnection)
            .flatMap(_ => config.connections.map(c => endpoints.offer(Permit(c))))
            .foldLeft[F[Unit]](Applicative[F].unit)((agg, p) => agg *> p)

          // run loop on a separate thread pool to minimize enqueue requests latency
          fiber <- Async[F].evalOn(
            GenConcurrent[F].start(loop(endpoints).foreverM.void),
            ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
          )
        } yield fiber

      private[this] def loop(workersQueue: Queue[F, Permit]): F[Unit] =
        for {
          request <- requestQueue.take
          permit <- workersQueue.take
          _ <- Logger[F].info(s"Schedule request to ${permit.connection}")
          // execute request in a separate fiber
          _ <- GenConcurrent[F].start(executeRequest(request, workersQueue, permit))
        } yield ()

      private[this] def executeRequest(promise: Deferred[F, FortuneError Either Fortune.FortuneResponse],
                                       workersQueue: Queue[F, Permit],
                                       permit: Permit): F[Unit] =
        for {
          _ <- activeRequestsStats.update(_ + 1)
          _ <- Logger[F].info(s"Executing request to ${permit.connection}")
          result <- fortuneService.get(permit.connection)
          _ <- activeRequestsStats.update(_ - 1)
          _ <- workersQueue.offer(permit)
          _ <- promise.complete(result)
        } yield ()
