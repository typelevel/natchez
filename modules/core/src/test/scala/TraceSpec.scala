package natchez

import cats.data.Kleisli
import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.{Applicative, FlatMap, Functor}
import org.specs2.mutable.Specification

object TraceSpec extends Specification {

  "Kleisli Trace" should {

    "generate spans" in {
      val fa = for {
        collector <- CollectorSpan.create[IO]
        _         <- mkSpans[Kleisli[IO, Span[IO], *]].run(collector)
        result    <- collector.state.get
      } yield result

      fa.unsafeRunSync() must_== expectedSpanState
    }

    "generate spans for Resource" in {
      val fa = for {
        collector <- CollectorSpan.create[IO]
        _         <- mkSpans[Resource[Kleisli[IO, Span[IO], *], *]].use(_ => Kleisli.pure(())).run(collector)
        result    <- collector.state.get
      } yield result

      fa.unsafeRunSync() must_== expectedSpanState
    }

  }

  "Noop Trace" should {

    "not generate spans" in {
      implicit val trace: Trace[Kleisli[IO, Span[IO], *]] = Trace.Implicits.noop

      val fa = for {
        collector <- CollectorSpan.create[IO]
        _         <- mkSpans[Kleisli[IO, Span[IO], *]].run(collector)
        result    <- collector.state.get
      } yield result

      fa.unsafeRunSync() must_== SpanState.Root
    }

  }

  private def expectedSpanState: SpanState = {
    val first = SpanState(
      name   = "first",
      kernel = Kernel(Map.empty),
      fields = List("first" -> 1),
      parent = Some(SpanState.Root)
    )

    val second = SpanState(
      name   = "second",
      kernel = Kernel(Map.empty),
      fields = List("second" -> 2),
      parent = Some(first)
    )

    val third = SpanState(
      name   = "third",
      kernel = Kernel(Map.empty),
      fields = List("third" -> 3),
      parent = Some(second)
    )

    third
  }

  private def mkSpans[F[_]: FlatMap: Applicative: Trace]: F[Unit] =
    Trace[F].span("first") {
      Trace[F].put("first" -> 1) >> Trace[F].span("second") {
        Trace[F].put("second" -> 2) >> Trace[F].span("third") {
          Trace[F].put("third" -> 3)
        }
      }
    }

}

final class CollectorSpan[F[_]: Functor: Applicative](val state: Ref[F, SpanState]) extends Span[F] {

  override def put(fields: (String, TraceValue)*): F[Unit] =
    state.update(v => v.copy(fields = v.fields ++ fields))

  override def kernel: F[Kernel] =
    state.get.map(_.kernel)

  override def span(name: String): Resource[F, Span[F]] =
    Resource.liftF(state.update(s => SpanState.withParent(name, s)).as(this))

}

object CollectorSpan {
  def create[F[_]: Sync]: F[CollectorSpan[F]] =
    Ref.of[F, SpanState](SpanState.Root).map(spans => new CollectorSpan[F](spans))
}

final case class SpanState(
    name: String,
    kernel: Kernel,
    fields: List[(String, TraceValue)],
    parent: Option[SpanState]
)

object SpanState {
  val Root: SpanState = SpanState("root", Kernel(Map.empty), Nil, None)

  def withParent(name: String, parent: SpanState) = SpanState(name, Kernel(Map.empty), Nil, Some(parent))
}