// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package log

import cats.effect.std.UUIDGen
import cats.effect.{Resource, Sync}
import cats.syntax.functor._
import org.typelevel.log4cats.Logger
import io.circe.Json

object Log {

  def entryPoint[F[_] : Sync : Logger : UUIDGen](
      service: String,
      format: Json => String = _.spaces2
  ): EntryPoint[F] =
    new EntryPoint[F] {

      def make(span: F[LogSpan[F]]): Resource[F, Span[F]] =
        Resource.makeCase(span)(LogSpan.finish(format)).widen

      override def continue(
          name: String,
          kernel: Kernel,
          options: Span.Options
      ): Resource[F, Span[F]] =
        make(LogSpan.fromKernel(service, name, kernel, options))

      override def continueOrElseRoot(
          name: String,
          kernel: Kernel,
          options: Span.Options
      ): Resource[F, Span[F]] =
        make(LogSpan.fromKernelOrElseRoot(service, name, kernel, options))

      override def root(name: String, options: Span.Options): Resource[F, Span[F]] =
        make(LogSpan.root(service, name, options))

    }

  // only maintained for binary compatibility
  @deprecated("use version with implicit UUIDGen", "0.3.8")
  def entryPoint[F[_]](service: String,
                       format: Json => String,
                       F: Sync[F],
                       L: Logger[F]
                      ): EntryPoint[F] = entryPoint(service, format)(F, L, UUIDGen.fromSync(F))

}
