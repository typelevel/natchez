// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.data.{Chain, Kleisli}
import cats.effect.MonadCancel
import cats.effect.Resource
import cats.effect.Resource.ExitCase
import cats.syntax.applicative._
import cats.{Applicative, ~>}

import java.net.URI

/** An span that can be passed around and used to create child spans. */
trait Span[F[_]] {

  /** Puts a sequence of fields into this span. */
  def put(fields: (String, TraceValue)*): F[Unit]

  /** Logs a sequence of fields on this span. */
  def log(fields: (String, TraceValue)*): F[Unit]

  /** Logs a single event on this span. */
  def log(event: String): F[Unit]

  /** Adds error information to this span. */
  def attachError(err: Throwable): F[Unit]

  /** The kernel for this span, which can be sent as headers to remote systems, which can then
    * continue this trace by constructing spans that are children of this one.
    */
  def kernel: F[Kernel]

  /** Resource that yields a child span of this span. */
  def span(name: String, options: Span.Options = Span.Options.Defaults): Resource[F, Span[F]]

  /** A unique ID for the trace of this span, if available.
    * This can be useful to include in error messages for example, so you can quickly find the associated trace.
    */
  def traceId: F[Option[String]]

  /** A unique ID for this span, if available. This can be useful to include in error messages for
    * example, so you can quickly find the associated trace.
    */
  def spanId: F[Option[String]]

  /** A unique URI for this trace, if available. This can be useful to include in error messages for
    * example, so you can quickly find the associated trace.
    */
  def traceUri: F[Option[URI]]

  /** Converts this `Span[F]` to a `Span[G]` using a `F ~> G`. */
  def mapK[G[_]](f: F ~> G)(implicit
      F: MonadCancel[F, _],
      G: MonadCancel[G, _]
  ): Span[G] = {
    val outer = this
    new Span[G] {
      override def put(fields: (String, TraceValue)*): G[Unit] = f(
        outer.put(fields: _*)
      )

      override def kernel: G[Kernel] = f(outer.kernel)

      override def attachError(err: Throwable) =
        f(outer.attachError(err))

      override def log(event: String) =
        f(outer.log(event))

      override def log(fields: (String, TraceValue)*) =
        f(outer.log(fields: _*))

      override def span(name: String, options: Span.Options): Resource[G, Span[G]] = outer
        .span(name, options)
        .map(_.mapK(f))
        .mapK(f)

      override def spanId: G[Option[String]] = f(outer.spanId)

      override def traceId: G[Option[String]] = f(outer.traceId)

      override def traceUri: G[Option[URI]] = f(outer.traceUri)
    }
  }
}

object Span {

  abstract class Default[F[_]: Applicative] extends Span[F] {
    protected val spanCreationPolicyOverride: Options.SpanCreationPolicy

    override final def span(name: String, options: Options): Resource[F, Span[F]] =
      spanCreationPolicyOverride match {
        case Options.SpanCreationPolicy.Suppress => Resource.pure(Span.noop[F])
        case Options.SpanCreationPolicy.Coalesce => Resource.pure(this)
        case Options.SpanCreationPolicy.Default  => makeSpan(name, options)
      }

    /** Like `span` but always creates a child span -- i.e., `options.spanCreationPolicy` is ignored. */
    def makeSpan(name: String, options: Options): Resource[F, Span[F]]
  }

  /** Ensure that Fields mixin data is added to a span when an error is raised.
    */
  def putErrorFields[F[_]: Applicative](span: Resource[F, Span[F]]): Resource[F, Span[F]] =
    span.flatMap(span =>
      Resource.makeCase(span.pure[F])((_, exit) =>
        exit match {
          case ExitCase.Errored(f: Fields) => span.put(f.fields.toList: _*)
          case _                           => Applicative[F].unit
        }
      )
    )

  /** A no-op `Span` implementation which ignores all child span creation.
    */
  def noop[F[_]: Applicative]: Span[F] = new NoopSpan

  /** A `Span` implementation which creates a new root span using the supplied `EntryPoint`
    * for each requested child span.
    */
  def makeRoots[F[_]: Applicative](ep: EntryPoint[F]): Span[F] = new RootsSpan(ep)

  private abstract class EphemeralSpan[F[_]: Applicative] extends Span[F] {
    override def put(fields: (String, TraceValue)*): F[Unit] = ().pure[F]
    override def kernel: F[Kernel] = Kernel(Map.empty).pure[F]
    override def attachError(err: Throwable) = ().pure[F]
    override def log(event: String) = ().pure[F]
    override def log(fields: (String, TraceValue)*) = ().pure[F]
    override def spanId: F[Option[String]] = (None: Option[String]).pure[F]
    override def traceId: F[Option[String]] = (None: Option[String]).pure[F]
    override def traceUri: F[Option[URI]] = (None: Option[URI]).pure[F]
  }

  private class NoopSpan[F[_]: Applicative] extends EphemeralSpan[F] {
    override def span(name: String, options: Span.Options): Resource[F, Span[F]] =
      Resource.pure(this)
  }

  private class RootsSpan[F[_]: Applicative](ep: EntryPoint[F]) extends EphemeralSpan[F] {
    override def span(name: String, options: Span.Options): Resource[F, Span[F]] =
      options.parentKernel.fold(ep.root(name))(ep.continueOrElseRoot(name, _))
  }

  private def resolve[F[_]](span: Span[F]): Kleisli[F, Span[F], *] ~> F =
    new (Kleisli[F, Span[F], *] ~> F) {
      def apply[A](k: Kleisli[F, Span[F], A]) = k(span)
    }

  /** Resolves a `Kleisli[F, Span[F], A]` to a `F[A]` by ignoring all span creation.
    */
  def dropTracing[F[_]: Applicative]: Kleisli[F, Span[F], *] ~> F = resolve(noop)

  /** Resolves a `Kleisli[F, Span[F], A]` to a `F[A]` by creating a new root span for each direct child span.
    */
  def rootTracing[F[_]: Applicative](ep: EntryPoint[F]): Kleisli[F, Span[F], *] ~> F = resolve(
    makeRoots(ep)
  )

  /** Options for creating a new span. */
  sealed trait Options {

    /** Optional parent kernel for the child span, in addition to the parent span.
      *
      * Some backends do not support multiple parents, in which case the
      * parent span is preferred and this parent kernel is ignored.
      */
    def parentKernel: Option[Kernel]

    /** Specifies how additional span creation requests are handled on the new span. */
    def spanCreationPolicy: Options.SpanCreationPolicy
    def spanKind: Span.SpanKind
    def links: Chain[Kernel]

    def withParentKernel(kernel: Kernel): Options
    def withoutParentKernel: Options
    def withSpanCreationPolicy(p: Options.SpanCreationPolicy): Options

    def withSpanKind(spanKind: SpanKind): Options

    def withLink(kernel: Kernel): Options
  }

  object Options {
    sealed trait SpanCreationPolicy
    object SpanCreationPolicy {

      /** Span creation behaves normally. */
      case object Default extends SpanCreationPolicy

      /** Requests for span creation are ignored and any information provided to the returned span are also ignored. */
      case object Suppress extends SpanCreationPolicy

      /** Requests for span creation are ignored but information provided to the returned span are attached to the original span. */
      case object Coalesce extends SpanCreationPolicy
    }

    private case class OptionsImpl(
        parentKernel: Option[Kernel],
        spanCreationPolicy: SpanCreationPolicy,
        spanKind: SpanKind,
        links: Chain[Kernel]
    ) extends Options {
      override def withParentKernel(kernel: Kernel): Options =
        OptionsImpl(Some(kernel), spanCreationPolicy, spanKind, links)
      override def withoutParentKernel: Options =
        OptionsImpl(None, spanCreationPolicy, spanKind, links)
      override def withSpanCreationPolicy(p: SpanCreationPolicy): Options =
        OptionsImpl(parentKernel, p, spanKind, links)
      override def withSpanKind(spanKind: SpanKind): Options =
        OptionsImpl(parentKernel, spanCreationPolicy, spanKind, links)
      override def withLink(kernel: Kernel): Options =
        OptionsImpl(parentKernel, spanCreationPolicy, spanKind, links.append(kernel))
    }

    val Defaults: Options =
      OptionsImpl(None, SpanCreationPolicy.Default, SpanKind.Internal, Chain.empty)
    val Suppress: Options = Defaults.withSpanCreationPolicy(SpanCreationPolicy.Suppress)
    val Coalesce: Options = Defaults.withSpanCreationPolicy(SpanCreationPolicy.Coalesce)

    def parentKernel(kernel: Kernel): Options = Defaults.withParentKernel(kernel)
  }

  sealed trait SpanKind

  object SpanKind {
    case object Internal extends SpanKind
    case object Client extends SpanKind
    case object Server extends SpanKind
    case object Producer extends SpanKind
    case object Consumer extends SpanKind
  }

}
