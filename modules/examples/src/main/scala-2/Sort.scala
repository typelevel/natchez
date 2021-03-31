// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats._
import cats.effect.Timer
import cats.syntax.all._
import natchez.Trace
import scala.concurrent.duration._

object Sort {

  // Intentionally slow parallel quicksort, to demonstrate branching. If we run too quickly it seems
  // to break Jaeger with "skipping clock skew adjustment" so let's pause a bit each time.
  def qsort[F[_]: Monad: Parallel: Trace: Timer, A: Order](as: List[A]): F[List[A]] =
    Trace[F].span(as.mkString(",")) {
      Timer[F].sleep(10.milli) *> {
          as match {
          case Nil    => Monad[F].pure(Nil)
          case h :: t =>
            val (a, b) = t.partition(_ <= h)
            (qsort[F, A](a), qsort[F, A](b)).parMapN(_ ++ List(h) ++ _)
        }
      }
    }
}
