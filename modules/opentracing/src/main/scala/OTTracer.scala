// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package opentracing

import cats.Applicative
import cats.effect.{ExitCase, Resource, Sync}
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.applicativeError._
import io.opentracing.log.Fields
import io.{opentracing => ot}
import io.opentracing.propagation.{Format, TextMapAdapter}

import scala.jdk.CollectionConverters._

object OTTracer {

  private [opentracing] def makeSpan[F[_] : Sync](t: ot.Tracer)(acquireSpan: F[ot.Span]): Resource[F, Span[F]] =
    Resource.makeCase(acquireSpan)(finishSpan(t)).map(OTSpan(t, _))

  private def finishSpan[F[_] : Sync](t: ot.Tracer): (ot.Span, ExitCase[Throwable]) => F[Unit] =
    (s, exitCase) => {
      def attachPossibleException: ExitCase[Throwable] => F[Unit] = {
        case ExitCase.Error(ex) =>
          val map: java.util.Map[String, Throwable] = new java.util.HashMap[String, Throwable]
          map.put(Fields.ERROR_OBJECT, ex)

          Sync[F].delay(s.log(map)).void
        case _ => Applicative[F].unit
      }

      attachPossibleException(exitCase) >> Sync[F].delay {
        t.scopeManager().activate(s)
        s.finish()
      }
    }

  def entryPoint[F[_] : Sync](acquireTracer: F[ot.Tracer]): Resource[F, EntryPoint[F]] =
    Resource.fromAutoCloseable(acquireTracer)
      .map(t => new EntryPoint[F] {
        override def root(name: String): Resource[F, Span[F]] =
          makeSpan(t)(Sync[F].delay(t.buildSpan(name).start()))

        override def continue(name: String, kernel: Kernel): Resource[F, Span[F]] = {
          val acquireSpan: F[ot.Span] = Sync[F].delay {
            val p = t.extract(
              Format.Builtin.HTTP_HEADERS,
              new TextMapAdapter(kernel.toHeaders.asJava)
            )
            t.buildSpan(name).asChildOf(p).start()
          }

          makeSpan(t)(acquireSpan)
        }

        override def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
          continue(name, kernel) flatMap {
            case null => root(name) // hurr, means headers are incomplete or invalid
            case a    => a.pure[Resource[F, *]]
          } recoverWith {
            case _: Exception => root(name)
          }

      })

}
