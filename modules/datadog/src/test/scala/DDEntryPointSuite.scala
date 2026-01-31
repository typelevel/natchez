// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package datadog

import cats.effect._
import cats.effect.std.AtomicCell
import cats.syntax.all._
import munit.CatsEffectSuite

class DDEntryPointSuite extends CatsEffectSuite {

  /** This test reproduces a bug where DDEntryPoint.continue() was missing
    * ignoreActiveSpan(), causing trace ID collisions in fiber-based runtimes.
    *
    * The Datadog tracer uses thread-local storage for the "active span".
    * Without ignoreActiveSpan(), when continue() is called with an empty kernel,
    * the tracer falls back to the thread's currently active span, causing
    * unrelated requests to share trace IDs.
    *
    * Expected: Each concurrent request gets a unique trace ID (100 unique IDs)
    * Bug behavior: Multiple requests share trace IDs (~20 unique IDs)
    */
  test("DDEntryPoint.continueOrElseRoot should generate unique trace IDs under concurrency") {
    DDTracer
      .entryPoint[IO] { builder =>
        IO.delay(builder.serviceName("test-service").build())
      }
      .use { ep =>
        for {
          traceIds <- AtomicCell[IO].of(List.empty[Option[String]])

          // Process 100 concurrent requests with empty kernels (no tracing headers)
          // This simulates external requests that don't carry incoming trace context
          _ <- (1 to 100).toList.parTraverse { _ =>
            ep.continueOrElseRoot("request", Kernel(Map.empty)).use { span =>
              span.traceId.flatMap(id => traceIds.update(_ :+ id))
            }
          }

          ids <- traceIds.get
        } yield {
          val uniqueIds = ids.flatten.distinct
          // With the fix (ignoreActiveSpan), we should get 100 unique trace IDs
          // Without the fix, we get ~20 unique IDs due to thread-local pollution
          assertEquals(
            uniqueIds.size,
            100,
            s"Expected 100 unique trace IDs but got ${uniqueIds.size}. " +
              "This indicates trace ID collision due to missing ignoreActiveSpan() in continue()."
          )
        }
      }
  }

  /** Verify that root() correctly generates unique trace IDs.
    * root() already uses ignoreActiveSpan() so this should always pass.
    */
  test("DDEntryPoint.root should generate unique trace IDs under concurrency") {
    DDTracer
      .entryPoint[IO] { builder =>
        IO.delay(builder.serviceName("test-service").build())
      }
      .use { ep =>
        for {
          traceIds <- AtomicCell[IO].of(List.empty[Option[String]])

          _ <- (1 to 100).toList.parTraverse { _ =>
            ep.root("request").use { span =>
              span.traceId.flatMap(id => traceIds.update(_ :+ id))
            }
          }

          ids <- traceIds.get
        } yield {
          val uniqueIds = ids.flatten.distinct
          assertEquals(uniqueIds.size, 100)
        }
      }
  }
}
