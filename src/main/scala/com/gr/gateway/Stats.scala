package com.gr.gateway

import cats.{Applicative, FlatMap, Monad}
import cats.effect.std.{AtomicCell, Queue}
import cats.syntax.all.*
import io.circe.{Encoder, Json}
import org.http4s.EntityEncoder
import org.http4s.circe.*

trait Stats[F[_]]:

  def waitingRequests(): F[Stats.Data]

object Stats:

  final case class Data(waitingRequests: Int, activeRequests: Int)

  object Data:
    given Encoder[Data] = new Encoder[Data]:
      final def apply(a: Data): Json = Json.obj(
        ("waitingRequests", Json.fromInt(a.waitingRequests)),
        ("activeRequests", Json.fromInt(a.activeRequests)),
      )

    given[F[_]]: EntityEncoder[F, Data] =
      jsonEncoderOf[F, Data]

  def impl[F[_] : Monad](requestsQueue: Queue[F, _], activeRequestsStats: AtomicCell[F, Int]): Stats[F] = new Stats[F]:
    def waitingRequests(): F[Stats.Data] =
      for {
        waitingRequests <- requestsQueue.size
        activeRequests <- activeRequestsStats.get
      } yield Data(waitingRequests, activeRequests)
