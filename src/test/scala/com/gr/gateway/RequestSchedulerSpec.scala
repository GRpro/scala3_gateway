package com.gr.gateway
import cats.Monad
import cats.effect.{Deferred, IO}
import cats.effect.std.{AtomicCell, Queue}
import com.gr.gateway.Fortune.FortuneError
import com.gr.gateway.config.{EndpointConfig, GatewayConfig}
import munit.CatsEffectSuite
import scala.concurrent.duration._


class RequestSchedulerSpec extends CatsEffectSuite:

  private[this] val endpoint1 = EndpointConfig(
    "localhost",
    9991,
    "/a"
  )
  private[this] val endpoint2 = EndpointConfig(
    "localhost",
    9992,
    "/b"
  )

  test("Scheduling in round robin order") {
    val config = GatewayConfig(
      connections = List(
        endpoint1,
        endpoint2
      ),
      maxParallelRequestsPerConnection = 1
    )

    for {
      stats <- AtomicCell[IO].of(0)
      requestQueue <- Queue.unbounded[IO, Deferred[IO, Either[FortuneError, Fortune.FortuneResponse]]]

      verificationQueue <- Queue.unbounded[IO, EndpointConfig]
      fortune = new Fortune[IO]:
        override def get(endpoint: EndpointConfig): IO[FortuneError Either Fortune.FortuneResponse] =
          verificationQueue.offer(endpoint) *> IO.pure(Right("fortune"))

      fiber <- RequestScheduler.impl(config, fortune, stats, requestQueue).run()

      p1 <- Deferred[IO, Either[FortuneError, Fortune.FortuneResponse]]
      p2 <- Deferred[IO, Either[FortuneError, Fortune.FortuneResponse]]
      _ <- requestQueue.offer(p1)
      _ <- requestQueue.offer(p2)

      // Check workers are selected in round robin order
      _ <- assertIO(verificationQueue.take, endpoint1)
      _ <- assertIO(p1.get, Right("fortune"))
      _ <- assertIO(verificationQueue.take, endpoint2)
      _ <- assertIO(p2.get, Right("fortune"))

      p3 <- Deferred[IO, Either[FortuneError, Fortune.FortuneResponse]]
      p4 <- Deferred[IO, Either[FortuneError, Fortune.FortuneResponse]]
      _ <- requestQueue.offer(p3)
      _ <- requestQueue.offer(p4)

      // Check order again
      _ <- assertIO(verificationQueue.take, endpoint1)
      _ <- assertIO(verificationQueue.take, endpoint2)

      _ <- fiber.cancel
    } yield ()


    test("Active requests are recorded") {

      val config = GatewayConfig(
        connections = List(
          endpoint1
        ),
        maxParallelRequestsPerConnection = 1
      )
    }
      for {
        stats <- AtomicCell[IO].of(0)
        requestQueue <- Queue.unbounded[IO, Deferred[IO, Either[FortuneError, Fortune.FortuneResponse]]]

        controlQueue <- Queue.unbounded[IO, Unit]

        fortune = new Fortune[IO]:
          override def get(endpoint: EndpointConfig): IO[FortuneError Either Fortune.FortuneResponse] =
            controlQueue.take *> IO.pure(Right("fortune"))

        fiber <- RequestScheduler.impl(config, fortune, stats, requestQueue).run()

        p1 <- Deferred[IO, Either[FortuneError, Fortune.FortuneResponse]]
        _ <- requestQueue.offer(p1)

        // 1 active request
        _ <- Monad[IO].whileM_(stats.get.map(_ != 1))(
          IO.println("waiting until stat is 1") *> IO.sleep(200.milliseconds)
        )

        // finish request
        _ <- controlQueue.offer(())

        // wait until 0 active requests again
        _ <- Monad[IO].whileM_(stats.get.map(_ != 0))(
          IO.println("waiting until stat is 0") *> IO.sleep(200.milliseconds)
        )

        _ <- fiber.cancel
      } yield ()
  }


