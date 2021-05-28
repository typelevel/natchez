// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.{~>, Applicative}
import cats.syntax.applicative._
import cats.data.Kleisli
import cats.effect.Resource
import cats.effect.Resource.ExitCase
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
   * A unique ID for the trace of this span, if available.
   * This can be useful to include in error messages for example, so you can quickly find the associated trace.
   */
  def traceId: F[Option[String]]

  /**
   * A unique ID for this span, if available. This can be useful to include in error messages for
   * example, so you can quickly find the associated trace.
   */
  def spanId: F[Option[String]]

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
      case ExitCase.Errored(f: Fields) => span.put(f.fields.toList: _*)
      case _ => Applicative[F].unit
    }))

  /**
    * A no-op `Span` implementation which ignors all child span creation.
    */
  def noop[F[_]: Applicative]: Span[F] = new NoopSpan

  /**
    * A `Span` implementation which creates a new root span using the supplied `EntryPoint`
    * for each requested child span.
    */
  def makeRoots[F[_]: Applicative](ep: EntryPoint[F]): Span[F] = new RootsSpan(ep)

  private abstract class EphemeralSpan[F[_]: Applicative] extends Span[F] {
    def put(fields: (String, TraceValue)*): F[Unit] = ().pure[F]
    def kernel: F[Kernel] = Kernel(Map.empty).pure[F]
    def traceId: F[Option[String]] = (None: Option[String]).pure[F]
    def spanId: F[Option[String]] = (None: Option[String]).pure[F]
    def traceUri: F[Option[URI]] = (None: Option[URI]).pure[F]
  }

  private class NoopSpan[F[_]: Applicative] extends EphemeralSpan[F] {
    def span(name: String): Resource[F, Span[F]] = Resource.pure(this)
  }

  private class RootsSpan[F[_]: Applicative](ep: EntryPoint[F]) extends EphemeralSpan[F] {
    def span(name: String): Resource[F, Span[F]] = ep.root(name)
  }

  private def resolve[F[_]](span: Span[F]): Kleisli[F, Span[F], *] ~> F =
    new (Kleisli[F, Span[F], *] ~> F) {
      def apply[A](k: Kleisli[F, Span[F], A]) = k(span)
    }

  /**
    * Resolves a `Kleisli[F, Span[F], A]` to a `F[A]` by ignoring all span creation.
    */
  def dropTracing[F[_]: Applicative]: Kleisli[F, Span[F], *] ~> F = resolve(noop)

  /**
    * Resolves a `Kleisli[F, Span[F], A]` to a `F[A]` by creating a new root span for each direct child span.
    */
  def rootTracing[F[_]: Applicative](ep: EntryPoint[F]): Kleisli[F, Span[F], *] ~> F = resolve(makeRoots(ep))
}
