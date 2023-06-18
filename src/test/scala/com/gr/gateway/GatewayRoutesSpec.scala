package com.gr.gateway

import cats.effect.kernel.GenConcurrent
import cats.effect.std.{AtomicCell, Queue}
import cats.effect.{Deferred, IO}
import com.gr.gateway.Fortune.{FortuneError, FortuneResponse}
import org.http4s.*
import org.http4s.implicits.*
import munit.CatsEffectSuite
import com.gr.gateway.Stats.Data._
import org.http4s.FormDataDecoder.formEntityDecoder

class GatewayRoutesSpec extends CatsEffectSuite:

  test("Fortune 200") {
    val res = getFortune(Right("funny not funny"))
    assertIO(res.map(_.status), Status.Ok) *>
      assertIO(res.flatMap(_.as[String]), "funny not funny")
  }

  test("Fortune 500") {
    val res = getFortune(Left(FortuneError(new RuntimeException("service unavailable"))))
    assertIO(res.map(_.status), Status.InternalServerError) *>
      assertIO(res.flatMap(_.as[String]), "service unavailable")
  }

  test("Stats 200") {
    val res = getStats
    assertIO(res.map(_.status), Status.Ok) *>
      assertIO(res.flatMap(_.as[String]), "{\"waitingRequests\":0,\"activeRequests\":1}")
  }

  private[this] def getFortune(expected: Either[FortuneError, Fortune.FortuneResponse]): IO[Response[IO]] =
    val getFortune = Request[IO](Method.GET, uri"/get-fortune")
    for {
      queue <- Queue.unbounded[IO, Deferred[IO, Either[FortuneError, Fortune.FortuneResponse]]]
      fiber <- GenConcurrent[IO].start(GatewayRoutes.fortuneRoutes(queue).orNotFound(getFortune))
      promise <- queue.take
      _ <- promise.complete(expected)
      res <- fiber.joinWith(IO.raiseError[Response[IO]](FortuneError(new RuntimeException("failed"))))
    } yield res

  private[this] def getStats: IO[Response[IO]] =
    val getStats = Request[IO](Method.GET, uri"/stats")
    for {
      queue <- Queue.unbounded[IO, Deferred[IO, Either[FortuneError, Fortune.FortuneResponse]]]
      activeStats <- AtomicCell[IO].of(1)
      stats = Stats.impl(queue, activeStats)
      res <- GatewayRoutes.statsRoutes(stats).orNotFound(getStats)
    } yield res

