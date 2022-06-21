// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package mock

import scala.jdk.CollectionConverters._

import cats.effect.{ Resource, Sync }
import io.opentracing.mock.MockTracer
import io.opentracing.propagation.{ Format, TextMapAdapter }

final case class MockEntrypoint[F[_] : Sync]() extends EntryPoint[F] {

  val mockTracer = new MockTracer()

  override def root(name: String): Resource[F, Span[F]] = {
    Resource
      .make(Sync[F].delay(mockTracer.buildSpan(name).start()))(
        span => Sync[F].delay(span.finish())
        )
      .map(MockSpan(mockTracer, _))
  }

  override def continue(
    name: String,
    kernel: Kernel
  ): Resource[F, Span[F]] = {
    Resource
      .make(
        Sync[F].delay {
          val spanCtxt = mockTracer.extract(
            Format.Builtin.HTTP_HEADERS,
            new TextMapAdapter(kernel.toHeaders.asJava)
            )
          mockTracer.buildSpan(name).asChildOf(spanCtxt).start()
        }
        )(span => Sync[F].delay(span.finish()))
      .map(MockSpan(mockTracer, _))
  }

  override def continueOrElseRoot(
    name: String,
    kernel: Kernel
  ): Resource[F, Span[F]] = {
    continue(name, kernel).flatMap {
      case null =>
        root(name)
      case span => Resource.pure[F, Span[F]](span)
    }
  }
}
