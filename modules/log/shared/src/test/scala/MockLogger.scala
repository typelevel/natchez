// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package log

import org.typelevel.log4cats.Logger
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.all._

trait MockLogger[F[_]] extends Logger[F] {
  def get: F[String]
}

object MockLogger {

  def newInstance[F[_]: Sync](name: String): F[MockLogger[F]] =
    Ref[F].of("").map { ref =>
      new MockLogger[F] {
        def error(message: => String): F[Unit] = ref.update(_ + s"$name: [error] $message\n")
        def warn(message: => String): F[Unit] = ref.update(_ + s"$name: [warn] $message\n")
        def info(message: => String): F[Unit] = ref.update(_ + s"$name: [info] $message\n")
        def debug(message: => String): F[Unit] = ref.update(_ + s"$name: [debug] $message\n")
        def trace(message: => String): F[Unit] = ref.update(_ + s"$name: [trace] $message\n")
        def error(t: Throwable)(message: => String): F[Unit] = ref.update(_ + s"$name: [error] $message\n${t.getMessage}")
        def warn(t: Throwable)(message: => String): F[Unit] = ref.update(_ + s"$name: [warn] $message\n${t.getMessage}")
        def info(t: Throwable)(message: => String): F[Unit] = ref.update(_ + s"$name: [info] $message\n${t.getMessage}")
        def debug(t: Throwable)(message: => String): F[Unit] = ref.update(_ + s"$name: [debug] $message\n${t.getMessage}")
        def trace(t: Throwable)(message: => String): F[Unit] = ref.update(_ + s"$name: [trace] $message\n${t.getMessage}")
        def get: F[String] = ref.get
      }
    }

}