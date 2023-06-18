package com.gr.gateway

import cats.{Functor, Monad, Traverse}
import cats.effect.{Async, Deferred, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.gr.gateway.Fortune.FortuneError
import com.gr.gateway.config.{Config, EndpointConfig, GatewayConfig, HttpConfig}
import cats.effect.std.{AtomicCell, Queue}
import fs2.io.net.Network
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger


object GatewayServer:

  def run[F[_] : Monad : Async : Network]: F[Nothing] = {
    for {
      client <- EmberClientBuilder.default[F].build
      config <- Resource.eval(Config.load())

      fortuneAlg = Fortune.impl[F](client)

      requestQueue <- Resource.eval(
        Queue.unbounded[F, Deferred[F, Either[FortuneError, Fortune.FortuneResponse]]]
      )
      activeRequestsStats <- Resource.eval(AtomicCell[F].of(0))

      statsAlg = Stats.impl[F](requestQueue, activeRequestsStats)

      requestSchedulerFiber <- Resource.eval(
        RequestScheduler.impl(config.gateway, fortuneAlg, activeRequestsStats, requestQueue).run()
      )

      httpApp = (
        GatewayRoutes.statsRoutes[F](statsAlg) <+>
          GatewayRoutes.fortuneRoutes[F](requestQueue)
        ).orNotFound

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      _ <-
        EmberServerBuilder.default[F]
          .withHost(Host.fromString(config.http.host).getOrElse(throw new RuntimeException(s"invalid host ${config.http.host}")))
          .withPort(Port.fromInt(config.http.port).getOrElse(throw new RuntimeException(s"invalid port ${config.http.port}")))
          .withHttpApp(finalHttpApp)
          .build

      _ <- Resource.eval(requestSchedulerFiber.join)
    } yield ()
  }.useForever
