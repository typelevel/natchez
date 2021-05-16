// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats._
import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, Timer}
import cats.syntax.all._
import natchez.{Span, Trace}
import natchez.fs2._

import scala.concurrent.duration._
import _root_.fs2._
import _root_.fs2.concurrent.Queue

object Sort {

  // Intentionally slow parallel quicksort, to demonstrate branching. If we run too quickly it seems
  // to break Jaeger with "skipping clock skew adjustment" so let's pause a bit each time.
  def qsort[F[_] : Concurrent : Parallel : Timer, A: Order](as: List[A])(implicit trace: Trace[F]): F[List[A]] = {
    case class ConcatRequest(left: List[A], mid: A, right: List[A], span: Span[trace.Sp], cb: List[A] => F[Unit])

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
                    trace.span("request concat") {
                      trace.current.flatMap { span =>
                        q.enqueue1(ConcatRequest(l, h, r, span, deferred.complete))
                      } *> deferred.get
                    }
                  }
                }
            }
          }
        }

      Stream.eval(qsort(as)).concurrently {
        Trace[Stream[F, *]].span("process concat reqeusts from queue") {
          q.dequeue.evalMap { case ConcatRequest(left, mid, right, span, cb) =>
            trace.liftSp(span.spanId).flatMap { spanId =>
              Trace[F].span(s"Asynchronously process ConcatRequest (for spanId $spanId)") {
                Trace[F].runWith(span) {
                  trace.span(s"concat ${left.mkString(",")} + $mid + ${right.mkString(",")}") {
                    cb(left ++ List(mid) ++ right)
                  }
                }
              }
            }
          }
        }
      }
    }.compile.lastOrError
  }
}
