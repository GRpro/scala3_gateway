package com.gr.gateway

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

object Main extends IOApp.Simple:
  val run = GatewayServer.run[IO]
