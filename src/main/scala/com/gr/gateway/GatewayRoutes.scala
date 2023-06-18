package com.gr.gateway

import cats.effect.kernel.GenConcurrent
import cats.effect.{Deferred, Sync}
import cats.effect.std.Queue
import cats.syntax.all.*
import com.gr.gateway.service.Fortune.FortuneError
import com.gr.gateway.service.{Fortune, Stats}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object GatewayRoutes:

  def fortuneRoutes[F[_]: GenConcurrentThrowable](Q: Queue[F, Deferred[F, Either[FortuneError, Fortune.FortuneResponse]]]): HttpRoutes[F] =
    val dsl = new Http4sDsl[F] {}
    import dsl.*
    HttpRoutes.of[F] {
      case GET -> Root / "get-fortune" =>
        for {
          request <- Deferred[F, Either[FortuneError, Fortune.FortuneResponse]]
          _ <- Q.offer(request)
          result <- request.get
          resp <- result match {
            case Left(error) => InternalServerError(error.e.getMessage)
            case Right(result) => Ok(result)
          }
        } yield resp
    }

  def statsRoutes[F[_]: Sync](S: Stats[F]): HttpRoutes[F] =
    val dsl = new Http4sDsl[F]{}
    import dsl.*
    HttpRoutes.of[F] {
      case GET -> Root / "stats" =>
        for {
          response <- S.waitingRequests()
          resp <- Ok(response)
        } yield resp
    }
