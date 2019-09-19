package natchez

import cats.effect._
import cats.implicits._
import org.specs2.mutable.Specification
import cats.{Applicative, MonadError}
import cats.data.Kleisli
import cats.effect.concurrent.Ref

object TraceSpec extends Specification {

  "Kleisli Trace" should {

    "generate spans" in {
      val fa = for {
        collector <- CollectorSpan.create[IO]
        _         <- mkSpans[Kleisli[IO, Span[IO], ?]].run(collector)
        result    <- collector.spans.get
      } yield result

      fa.unsafeRunSync() must_== List("first", "second", "third")
    }

    "generate spans for Resource" in {
      val fa = for {
        collector <- CollectorSpan.create[IO]
        _         <- mkSpans[Resource[Kleisli[IO, Span[IO], ?], ?]].use(_ => Kleisli.pure(())).run(collector)
        result    <- collector.spans.get
      } yield result

      fa.unsafeRunSync() must_== List("first", "second", "third")
    }

  }

  "Noop Trace" should {

    "not generate spans" in {
      implicit val trace: Trace[Kleisli[IO, Span[IO], ?]] = Trace.Implicits.noop

      val fa = for {
        collector <- CollectorSpan.create[IO]
        _         <- mkSpans[Kleisli[IO, Span[IO], ?]].run(collector)
        result    <- collector.spans.get
      } yield result

      fa.unsafeRunSync() must_== Nil
    }

  }

  private def mkSpans[G[_]: Applicative: Trace]: G[Unit] =
    Trace[G].span("first") {
      Trace[G].span("second") {
        Trace[G].span("third") {
          Applicative[G].unit
        }
      }
    }

}

final class CollectorSpan[F[_]](val spans: Ref[F, List[String]])(implicit F: MonadError[F, Throwable])
  extends Span[F] {

  override def put(fields: (String, TraceValue)*): F[Unit] =
    F.unit

  override def kernel: F[Kernel] =
    F.raiseError(new RuntimeException("Mock"))

  override def span(name: String): Resource[F, Span[F]] =
    Resource.liftF(spans.update(_ :+ name).as(this))

}

object CollectorSpan {
  def create[F[_]: Sync]: F[CollectorSpan[F]] =
    Ref.of[F, List[String]](Nil).map(spans => new CollectorSpan[F](spans))
}