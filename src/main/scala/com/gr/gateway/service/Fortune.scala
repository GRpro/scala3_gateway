package com.gr.gateway.service

import cats.data.EitherT
import cats.effect.Concurrent
import cats.syntax.*
import cats.syntax.all.*
import com.gr.gateway.service.Fortune
import com.gr.gateway.config.EndpointConfig
import com.gr.gateway.service.Fortune.FortuneError
import io.circe.{Decoder, Encoder}
import org.http4s.*
import org.http4s.Method.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.implicits.*

trait Fortune[F[_]]:
  def get(connectionConfig: EndpointConfig): F[FortuneError Either Fortune.FortuneResponse]

object Fortune:
  def apply[F[_]](using ev: Fortune[F]): Fortune[F] = ev

  type FortuneResponse = String

  object FortuneResponse:
    given Decoder[FortuneResponse] = Decoder.decodeString

    given[F[_] : Concurrent]: EntityDecoder[F, FortuneResponse] = jsonOf

  final case class FortuneError(e: Throwable) extends RuntimeException

  def impl[F[_] : Concurrent](C: Client[F]): Fortune[F] = new Fortune[F]:
    val dsl = new Http4sClientDsl[F] {}

    import dsl.*

    def get(connectionConfig: EndpointConfig): F[FortuneError Either Fortune.FortuneResponse] =
      EitherT(
        C.expect[FortuneResponse](GET(Uri.unsafeFromString(s"http://${connectionConfig.host}:${connectionConfig.port}${connectionConfig.path}")))
          .attempt
      )
        .leftMap(e => FortuneError(e))
        .value
