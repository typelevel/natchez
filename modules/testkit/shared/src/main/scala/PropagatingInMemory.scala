// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats._
import cats.data._
import cats.effect.{Trace => _, _}
import cats.syntax.all._
import natchez.Span.Options
import org.typelevel.ci._

import java.net.URI

/** An in-memory Natchez implementation that models trace *context propagation*, complementing
  * [[InMemory]] (which records a command log but gives every span the same, typically empty,
  * kernel and a `None` `spanId`).
  *
  * Here every span is assigned a distinct id which it exposes through both `spanId` and its
  * `kernel`. When a child span is created, its parent is resolved as: the id carried by an
  * explicit `parentKernel` (via `Span.Options.parentKernel`) if present, otherwise the enclosing
  * span. That resolved parent is exposed as [[Span.parentId]], and each created span is appended
  * to a shared log. Together these make it possible to assert that a span really is a child of
  * the span whose kernel was propagated to it — the property real backends implement, and the one
  * [[InMemory]] cannot express.
  *
  * Example:
  * {{{
  * for {
  *   ep    <- PropagatingInMemory.EntryPoint.create[IO]
  *   _     <- ep.root("root").use { root =>
  *              root.span("parent").use { parent =>
  *                parent.span("child").use(_ => IO.unit)
  *              }
  *            }
  *   spans <- ep.spans
  * } yield spans // child.parentId == parent.id
  * }}}
  */
object PropagatingInMemory {

  /** The header key under which a span's id travels inside a [[Kernel]], so a continued or child
    * span can observe the span it descends from.
    */
  val SpanIdHeader: CIString = ci"X-Natchez-Span-Id"

  /** A span that was created, captured at creation time. */
  final case class Recorded(name: String, id: String, parentId: Option[String])

  class Span[F[_]: Monad](
      val name: String,
      val id: String,
      // The resolved parent: an explicit parentKernel's id if one was given, else the enclosing
      // span's id, else `None` for a root with no continued kernel.
      val parentId: Option[String],
      log: Ref[F, Chain[Recorded]],
      nextId: F[String],
      val options: Options
  ) extends natchez.Span.Default[F] {
    override protected val spanCreationPolicyOverride: Options.SpanCreationPolicy =
      options.spanCreationPolicy

    // This span's kernel carries only its own id. A child reads this to link back to us.
    private val myKernel: Kernel = Kernel(Map(SpanIdHeader -> id))

    def put(fields: (String, natchez.TraceValue)*): F[Unit] = Applicative[F].unit
    def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] = Applicative[F].unit
    def log(event: String): F[Unit] = Applicative[F].unit
    def log(fields: (String, TraceValue)*): F[Unit] = Applicative[F].unit

    def kernel: F[Kernel] = myKernel.pure[F]
    def spanId: F[Option[String]] = id.some.pure[F]
    def traceId: F[Option[String]] = "trace".some.pure[F]
    def traceUri: F[Option[URI]] = none[URI].pure[F]

    def makeSpan(name: String, options: Options): Resource[F, natchez.Span[F]] = {
      // An explicit parentKernel (e.g. one propagated into a loader) wins over the enclosing span.
      val parentId = options.parentKernel.flatMap(_.toHeaders.get(SpanIdHeader)).orElse(id.some)
      Resource.eval(newSpan(name, parentId, log, nextId, options))
    }
  }

  private def newSpan[F[_]: Monad](
      name: String,
      parentId: Option[String],
      log: Ref[F, Chain[Recorded]],
      nextId: F[String],
      options: Options
  ): F[Span[F]] =
    nextId.flatMap { id =>
      log
        .update(_.append(Recorded(name, id, parentId)))
        .as(new Span(name, id, parentId, log, nextId, options))
    }

  class EntryPoint[F[_]: Monad](
      val log: Ref[F, Chain[Recorded]],
      nextId: F[String]
  ) extends natchez.EntryPoint[F] {

    /** All spans created so far, in creation order. */
    def spans: F[List[Recorded]] = log.get.map(_.toList)

    override def root(name: String, options: natchez.Span.Options): Resource[F, Span[F]] =
      Resource.eval(newSpan(name, none, log, nextId, options))

    override def continue(
        name: String,
        kernel: Kernel,
        options: natchez.Span.Options
    ): Resource[F, Span[F]] =
      Resource.eval(newSpan(name, kernel.toHeaders.get(SpanIdHeader), log, nextId, options))

    override def continueOrElseRoot(
        name: String,
        kernel: Kernel,
        options: natchez.Span.Options
    ): Resource[F, Span[F]] =
      continue(name, kernel, options)
  }

  object EntryPoint {
    def create[F[_]: Concurrent]: F[EntryPoint[F]] =
      (
        Ref.of[F, Chain[Recorded]](Chain.empty),
        Ref.of[F, Int](0)
      ).mapN { (log, counter) =>
        new EntryPoint(log, counter.getAndUpdate(_ + 1).map(n => s"span-$n"))
      }
  }

}
