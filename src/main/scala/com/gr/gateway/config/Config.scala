package com.gr.gateway.config

import cats.{Applicative, Monad}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import cats.syntax.*
import cats.implicits._
import scala.jdk.CollectionConverters._
import com.typesafe.config.impl.{ConfigInt, ConfigString}

case class Config(http: HttpConfig, gateway: GatewayConfig)
case class HttpConfig(host: String, port: Int)
case class GatewayConfig(connections: List[EndpointConfig], maxParallelRequestsPerConnection: Int)
case class EndpointConfig(host: String, port: Int, path: String)


object Config:

  def load[F[_]: Monad](): F[Config] =
    for {
      config <- Monad[F].pure(ConfigFactory.load())
    } yield Config(
      http = HttpConfig(
        host = config.getString("service.http.host"),
        port = config.getInt("service.http.port")
      ),
      gateway = GatewayConfig(
        connections = config.getObjectList("service.gateway.connections").asScala.map { obj =>
          val confObj = obj.toConfig
          EndpointConfig(
            host = confObj.getString("host"),
            port = confObj.getInt("port"),
            path = confObj.getString("path")
          )
        }.toList,
        maxParallelRequestsPerConnection = config.getInt("service.gateway.maxParallelRequestsPerConnection")
      )
    )
