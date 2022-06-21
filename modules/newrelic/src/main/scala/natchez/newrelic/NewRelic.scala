// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.newrelic

import cats.effect._
import cats.implicits._
import com.newrelic.telemetry.spans.SpanBatchSender
import natchez.{EntryPoint, Kernel, Span}

object NewRelic {

  def entryPoint[F[_]: Sync](system: String)(sender: SpanBatchSender): EntryPoint[F] =
    new EntryPoint[F] {

      def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
        Resource
          .make(NewrelicSpan.fromKernel[F](system, name, kernel)(sender))(s =>
            NewrelicSpan.finish[F](s))
          .widen

      def root(name: String): Resource[F, Span[F]] =
        Resource
          .make(NewrelicSpan.root[F](system, name, sender))(NewrelicSpan.finish[F])
          .widen

      def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
        continue(name, kernel).recoverWith {
          case _: NoSuchElementException => root(name)
        }

    }

}
