// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package datadog

import cats.effect._
import cats.syntax.all._
import io.opentracing.propagation.{Format, TextMapAdapter}
import io.{opentracing => ot}

import java.net.URI
import scala.jdk.CollectionConverters._

final class DDEntryPoint[F[_]: Sync](tracer: ot.Tracer, uriPrefix: Option[URI]) extends EntryPoint[F] {
  override def root(name: String): Resource[F, Span[F]] =
    Resource.make(
      Sync[F].delay(tracer.buildSpan(name).start()))(
      s => Sync[F].delay(s.finish()))
    .map(DDSpan(tracer, _, uriPrefix))

  override def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
    Resource.make(
      Sync[F].delay {
        val spanContext = tracer.extract(
          Format.Builtin.HTTP_HEADERS,
          new TextMapAdapter(kernel.toHeaders.asJava)
        )
        tracer.buildSpan(name).asChildOf(spanContext).start()
      }
    )(s => Sync[F].delay(s.finish())).map(DDSpan(tracer, _, uriPrefix))

  override def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
    continue(name, kernel) flatMap {
      case null => root(name) // hurr, means headers are incomplete or invalid
      case span => span.pure[Resource[F, *]]
    }
}
