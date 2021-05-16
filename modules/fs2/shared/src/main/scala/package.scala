// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.effect.Concurrent
import cats.~>
import _root_.fs2.Stream

package object fs2 {
  implicit def natchezFs2TraceForStream[F[_]: Concurrent](implicit t: Trace[F]): Trace.Aux[Stream[F, *], t.Sp] =
    new Trace.Lifted[F, Stream[F, *], t.Sp](t) {

      def lift[A](fa: F[A]): Stream[F, A] = Stream.eval(fa)

      override def span[A](name: String)(k: Stream[F, A]): Stream[F, A] =
        current.flatMap { span =>
          Stream.resource(span.span(name).mapK(trace.liftSpK)).flatMap { span =>
            runWith(span)(k)
          }
        }

      override def runWith[A](span: Span[Sp])(k: Stream[F, A]): Stream[F, A] =
        k.translateInterruptible(new (~>[F, F]) {
          def apply[A0](fa: F[A0]): F[A0] = trace.runWith(span)(fa)
        })
  }
}
