package natchez.http4s

import cats.data.{ Kleisli, OptionT }
import cats.effect.Bracket
import cats.implicits._
import org.http4s.HttpRoutes
import natchez.Trace
import natchez.Tags
import scala.util.control.NonFatal
import org.http4s.Response
import java.io.ByteArrayOutputStream
import java.io.PrintStream

  /**
   * A middleware that adds the following standard fields to the current span:
   *
   * - "http.method"      -> "GET", "PUT", etc.
   * - "http.url"         -> request URI (not URL)
   * - "http.status_code" -> "200", "403", etc. // why is this a string*
   * - "error"            -> true // only present in case of error
   *
   * In addition the following non-standard fields are added in case of error:
   *
   * - "error.message"    -> Exception message
   * - "error.stacktrace" -> Exception stack trace as a multi-line string
   */
  object NatchezMiddleware {

  def apply[F[_]: Bracket[*[_], Throwable]: Trace](routes: HttpRoutes[F]): HttpRoutes[F] =
    Kleisli { req =>

      val addRequestFields: F[Unit] =
        Trace[F].put(
          Tags.http.method(req.method.name),
          Tags.http.url(req.uri.renderString),
        )

      def addResponseFields(res: Response[F]): F[Unit] =
        Trace[F].put(
          Tags.http.status_code(res.status.code.toString)
        )

      def addErrorFields(e: Throwable): F[Unit] =
        Trace[F].put(
          Tags.error(true),
          "error.message"    -> {
            val baos = new ByteArrayOutputStream
            val fs   = new AnsiFilterStream(baos)
            val ps   = new PrintStream(fs, true, "UTF-8")
            e.printStackTrace(ps)
            ps.close
            fs.close
            baos.close
            new String(baos.toByteArray, "UTF-8")
          }
        )

      OptionT {
        routes(req).onError {
          case NonFatal(e)   => OptionT.liftF(addRequestFields *> addErrorFields(e))
        } .value.flatMap {
          case Some(handler) => addRequestFields *> addResponseFields(handler).as(handler.some)
          case None          => Option.empty[Response[F]].pure[F]
        }
      }
    }

}
