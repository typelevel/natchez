// Copyright (c) 2019-2021 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package lightstep

import cats.effect.{ Resource, Sync }
import cats.implicits._
import io.opentracing.Tracer
import io.opentracing.propagation.{ Format, TextMapAdapter }

import scala.jdk.CollectionConverters._

final class LightstepEntryPoint[F[_]: Sync](tracer: Tracer) extends EntryPoint[F] {
  override def root(name: String): Resource[F, Span[F]] =
    Resource
      .make(Sync[F].delay(tracer.buildSpan(name).start()))(s => Sync[F].delay(s.finish()))
      .map(LightstepSpan(tracer, _))

  override def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
    Resource.make(
      Sync[F].delay {
        val p = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(kernel.toHeaders.asJava))
        tracer.buildSpan(name).asChildOf(p).start()
      }
    )(s => Sync[F].delay(s.finish())).map(LightstepSpan(tracer, _))

  override def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
    continue(name, kernel).flatMap {
      case null => root(name)
      case a    => a.pure[Resource[F, *]]
    }
}
