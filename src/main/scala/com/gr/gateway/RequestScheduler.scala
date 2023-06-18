package com.gr.gateway

import cats.effect.kernel.{Fiber, GenConcurrent}
import cats.effect.std.{AtomicCell, Queue, Semaphore}
import cats.effect.{Async, Deferred}
import cats.implicits.*
import cats.syntax.all.*
import cats.{Applicative, Functor, Monad, Traverse}
import com.gr.gateway.Fortune.FortuneError
import com.gr.gateway.config.{EndpointConfig, GatewayConfig}
import com.gr.gateway.{Fortune, GenConcurrentThrowable}

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.ExecutionContext


trait RequestScheduler[F[_] : Monad : Async : GenConcurrentThrowable]:
  def run(): F[Fiber[F, Throwable, Unit]]

object RequestScheduler:

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

          // TODO log
          _ = println(s"Schedule request to ${permit.connection}")

          // execute request in a separate fiber
          _ <- GenConcurrent[F].start(executeRequest(request, workersQueue, permit))
        } yield ()

      private[this] def executeRequest(promise: Deferred[F, FortuneError Either Fortune.FortuneResponse],
                                       workersQueue: Queue[F, Permit],
                                       permit: Permit): F[Unit] =
        for {
          _ <- activeRequestsStats.update(_ + 1)
          result <- fortuneService.get(permit.connection)
          _ <- activeRequestsStats.update(_ - 1)

          // TODO log
          _ = println(s"Request to ${permit.connection}")
          _ <- workersQueue.offer(permit)
          _ <- promise.complete(result)
        } yield ()
