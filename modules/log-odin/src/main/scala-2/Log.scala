// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package logodin

import cats.effect.{ Resource, Sync }
import cats.implicits._
import io.odin.Logger

object Log {

  def entryPoint[F[_]: Sync: Logger](
    service: String,
  ): EntryPoint[F] =
    new EntryPoint[F] {

      def make(span: F[LogSpan[F]]): Resource[F, Span[F]] =
        Resource.makeCase(span)(LogSpan.finish).widen

      def continue(name: String, kernel: Kernel): Resource[F,Span[F]] =
        make(LogSpan.fromKernel(service, name, kernel))

      def continueOrElseRoot(name: String, kernel: Kernel): Resource[F,Span[F]] =
        make(LogSpan.fromKernelOrElseRoot(service, name, kernel))

      def root(name: String): Resource[F,Span[F]] =
        make(LogSpan.root(service, name))

    }


}
