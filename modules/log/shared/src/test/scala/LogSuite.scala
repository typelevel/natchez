// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package log

import munit.CatsEffectSuite
import cats.effect.IO
import cats.syntax.traverse._
import io.circe.Json
import natchez.Span.SpanKind

class LogSuite extends CatsEffectSuite {

  // Remove the time-sensitive properties
  def filter(j: Json): Json =
    j.fold(
      Json.Null,
      Json.fromBoolean,
      Json.fromJsonNumber,
      Json.fromString,
      values => Json.fromValues(values.map(filter)),
      obj =>
        Json.fromJsonObject(
          obj
            .remove("timestamp")
            .remove("duration_ms")
            .remove("trace.span_id")
            .remove("trace.parent_id")
            .remove("trace.trace_id")
            .remove("exit.error.stackTrace") // contains thread info
            .mapValues(filter)
        )
    )

  test("log formatter should log things") {
    MockLogger.newInstance[IO]("test").flatMap { implicit log =>
      Log
        .entryPoint[IO]("service", filter(_: Json).spaces2)
        .root("root span", Span.Options.Defaults.withSpanKind(SpanKind.Server))
        .use { root =>
          root.put("foo" -> 1, "bar" -> true) *>
            root.span("child").use { child =>
              child.put("baz" -> "qux")
            }
        } *> log.get.assertEquals {
        """|test: [info] {
           |  "name" : "root span",
           |  "service" : "service",
           |  "span.kind" : "Server",
           |  "span.links" : [
           |  ],
           |  "foo" : 1,
           |  "bar" : true,
           |  "exit.case" : "succeeded",
           |  "children" : [
           |    {
           |      "name" : "child",
           |      "service" : "service",
           |      "span.kind" : "Internal",
           |      "span.links" : [
           |      ],
           |      "baz" : "qux",
           |      "exit.case" : "succeeded",
           |      "children" : [
           |      ]
           |    }
           |  ]
           |}
           |""".stripMargin
      }
    }
  }

  test("continueOrElseRoot should preserve non-uuid trace from kernel") {
    MockLogger.newInstance[IO]("test").flatMap { implicit log =>
      val kernel = Kernel(
        Map(
          LogSpan.Headers.TraceId -> "non-uuid-trace-id",
          LogSpan.Headers.SpanId -> "non-uuid-parent-span-id"
        )
      )
      val traceId = Log
        .entryPoint[IO]("service", filter(_: Json).spaces2)
        .continueOrElseRoot("root span", kernel)
        .use { root =>
          root.traceId
        }

      assertIO(traceId, Some("non-uuid-trace-id"))
    }
  }

  test("log formatter should handle exceptions") {
    val exWithMsg = new RuntimeException("oops")
    val exNull = new RuntimeException(null: String)

    val tests = List[(Throwable, String)](
      exWithMsg -> """|test: [info] {
                      |  "name" : "root span",
                      |  "service" : "service",
                      |  "span.kind" : "Server",
                      |  "span.links" : [
                      |  ],
                      |  "exit.case" : "succeeded",
                      |  "exit.error.class" : "java.lang.RuntimeException",
                      |  "exit.error.message" : "oops",
                      |  "children" : [
                      |  ]
                      |}
                      |""".stripMargin,
      exNull -> """|test: [info] {
                   |  "name" : "root span",
                   |  "service" : "service",
                   |  "span.kind" : "Server",
                   |  "span.links" : [
                   |  ],
                   |  "exit.case" : "succeeded",
                   |  "exit.error.class" : "java.lang.RuntimeException",
                   |  "exit.error.message" : null,
                   |  "children" : [
                   |  ]
                   |}
                   |""".stripMargin
    )

    tests.traverse { case (exception, expected) =>
      MockLogger.newInstance[IO]("test").flatMap { implicit log =>
        Log
          .entryPoint[IO]("service", filter(_: Json).spaces2)
          .root("root span", Span.Options.Defaults.withSpanKind(SpanKind.Server))
          .use { root =>
            root.attachError(err = exception)
          } *> log.get.assertEquals(expected)
      }
    }
  }
}
