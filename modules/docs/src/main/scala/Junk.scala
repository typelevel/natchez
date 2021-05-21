// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package junk

import cats.effect.IO
import natchez.EntryPoint
import natchez.log.Log
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Junk {

  implicit val log: Logger[IO] =
    Slf4jLogger.getLoggerFromName("example-logger")

  val ep: EntryPoint[IO] =
    Log.entryPoint[IO]("example-service")

}