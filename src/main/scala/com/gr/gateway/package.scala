package com.gr

import cats.effect.kernel.GenConcurrent

package object gateway {

  type GenConcurrentThrowable = [F[_]] =>> GenConcurrent[F, Throwable]

}
