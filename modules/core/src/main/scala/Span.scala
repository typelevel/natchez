// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.Applicative, cats.syntax.applicative._
import cats.effect.{Resource, ExitCase}
import java.net.URI

/** An span that can be passed around and used to create child spans. */
trait Span[F[_]] {

  /** Put a sequence of fields into this span. */
  def put(fields: (String, TraceValue)*): F[Unit]

  /**
   * The kernel for this span, which can be sent as headers to remote systems, which can then
   * continue this trace by constructing spans that are children of this one.
   */
  def kernel: F[Kernel]

  /** Resource that yields a child span with the given name. */
  def span(name: String): Resource[F, Span[F]]

  /**
   * A unique ID for this trace, if available. This can be useful to include in error messages for
   * example, so you can quickly find the associated trace.
   */
  def traceId: F[Option[String]]

  /**
   * A unique URI for this trace, if available. This can be useful to include in error messages for
   * example, so you can quickly find the associated trace.
   */
  def traceUri: F[Option[URI]]

}

object Span {
  /** 
   * Ensure that Fields mixin data is added to a span when an error is raised.
   */
  def putErrorFields[F[_]: Applicative](span: Resource[F, Span[F]]): Resource[F, Span[F]] = 
    span.flatMap(span => Resource.makeCase(span.pure[F])((_, exit) => exit match {
      case ExitCase.Error(f: Fields) => span.put(f.fields.toList: _*)
      case _ => Applicative[F].unit
    }))
}
