// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats._
import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, Timer}
import cats.syntax.all._
import natchez.{Portal, Trace}
import fs2._
import fs2.concurrent.Queue

import scala.concurrent.duration._

object Sort {

  // Intentionally slow parallel quicksort, to demonstrate branching. If we run too quickly it seems
  // to break Jaeger with "skipping clock skew adjustment" so let's pause a bit each time.
  def qsort[F[_] : Concurrent : Parallel : Trace : Timer, A: Order](as: List[A]): F[List[A]] = {
    case class ConcatRequest(left: List[A], mid: A, right: List[A], portal: Portal[F], cb: List[A] => F[Unit])

    Stream.eval(Queue.unbounded[F, ConcatRequest]).flatMap { q =>

      def qsort(as: List[A]): F[List[A]] =
        Trace[F].span(s"sort ${as.mkString(",")}") {
          Timer[F].sleep(10.milli) *> {
            as match {
              case Nil => Monad[F].pure(Nil)
              case h :: t =>
                val (a, b) = t.partition(_ <= h)
                (qsort(a), qsort(b)).parTupled.flatMap { case (l, r) =>
                  Deferred[F, List[A]].flatMap { deferred =>
                    Trace[F].portal.flatMap { portal =>
                      q.enqueue1(ConcatRequest(l, h, r, portal, deferred.complete))
                    } *> deferred.get
                  }
                }
            }
          }
        }
      Stream.eval(qsort(as)).concurrently {
        q.dequeue.evalMap { case ConcatRequest(left, mid, right, portal, cb) =>
          portal {
            Trace[F].span(s"concat ${left.mkString(",")} + $mid + ${right.mkString(",")}") {
              cb(left ++ List(mid) ++ right)
            }
          }
        }
      }
    }.compile.lastOrError
  }
}

object Fs2StreamTrace {

}
