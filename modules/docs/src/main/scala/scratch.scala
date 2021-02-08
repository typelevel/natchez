// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

import org.http4s.dsl.io._
import org.http4s.HttpRoutes
import cats.effect._
import natchez.{ EntryPoint, Kernel }

object X {

  def routes(ep: EntryPoint[IO]) =
    HttpRoutes.of[IO] {
      case req@(GET -> Root / "hello" / name) =>

        val k: Kernel =
          Kernel(req.headers.toList.map { h => h.name.value -> h.value }.toMap)

        ep.continueOrElseRoot("hello", k).use { span =>
          span.put("the-name" -> name) *>
          Ok(s"Hello, $name.")
        }

    }

}