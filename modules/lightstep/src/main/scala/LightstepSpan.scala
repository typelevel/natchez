// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package lightstep

import cats.effect.{ Resource, Sync }
import io.{ opentracing => ot }

private[lightstep] final case class LightstepSpan[F[_]: Sync](
  tracer: ot.Tracer,
  span: ot.Span
) extends Span[F] {

  override def kernel: F[Kernel] = ???

  override def put(fields: (String, TraceValue)*): F[Unit] = ???

  override def span(name: String): Resource[F,Span[F]] = ???
}
