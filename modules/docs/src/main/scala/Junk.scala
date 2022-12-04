// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

import cats.effect.IO
import natchez.Span
import org.http4s.{EntityDecoder, Uri, Header}
import org.http4s.Method.GET
import org.http4s.client.Client
import org.http4s.client.dsl.io._

object X {

  def makeRequest[A](span: Span[IO], client: Client[IO], uri: Uri)(implicit
      ev: EntityDecoder[IO, A]
  ): IO[A] =
    span.kernel.flatMap { k =>
      // turn a Map[String, String] into List[Header]
      val http4sHeaders = k.toHeaders.map { case (k, v) => Header.Raw(k, v) }.toSeq
      client.expect[A](GET(uri).withHeaders(http4sHeaders))
    }

}
