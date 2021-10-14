// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.opentracing

import cats.effect.implicits._
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.syntax.all._
import io.opentracing
import natchez.EntryPoint
import natchez.Kernel
import natchez.Span

import java.net.URI
import scala.jdk.CollectionConverters._

final class OTEntryPoint[F[_]: Sync](tracer: opentracing.Tracer, mkUri: Option[MakeSpanUri])
  extends EntryPoint[F] {

  override def root(name: String): Resource[F, Span[F]] = OTSpan.make[F](name, None, tracer, mkUri)

  override def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
    Sync[F].delay {
      tracer.extract(
        opentracing.propagation.Format.Builtin.HTTP_HEADERS,
        new opentracing.propagation.TextMapAdapter(kernel.toHeaders.asJava),
      )
    }.toResource.flatMap(ctx => OTSpan.make(name, Some(ctx), tracer, mkUri))

  override def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
    continue(name, kernel).flatMap {
      case null =>
        root(name) // hurr, means headers are incomplete or invalid
      case span =>
        span.pure[Resource[F, *]]
    }

}

trait MakeSpanUri {
  def makeUri(traceId: String, spanId: String): URI
}
