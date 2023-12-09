// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package xray

import cats.effect.std.Env
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import natchez.xray.XRayEnvironmentSuite.TraceId
import org.scalacheck.effect.PropF.forAllNoShrinkF
import org.scalacheck.{Arbitrary, Gen}

import scala.collection.immutable

class XRayEnvironmentSuite extends CatsEffectSuite with ScalaCheckEffectSuite {

  test("when using a Sync[F], get the Trace ID from one of many possible sources") {
    forAllNoShrinkF { (maybeSystemProperty: Option[TraceId], maybeEnvVar: Option[TraceId]) =>
      implicit val env: Env[IO] = new Env[IO] {
        override def get(name: String): IO[Option[String]] =
          entries.map(_.toMap.get(name))

        override def entries: IO[immutable.Iterable[(String, String)]] =
          maybeEnvVar
            .map(_.value)
            .toList
            .tupleLeft("_X_AMZN_TRACE_ID")
            .pure[IO]
      }

      maybeSystemProperty
        .traverse { s =>
          Resource
            .make(IO(sys.props.+=("com.amazonaws.xray.traceHeader" -> s.value))) { _ =>
              IO(sys.props.-=("com.amazonaws.xray.traceHeader")).void
            }
        }
        .surround {
          XRayEnvironment[IO].traceId
            .map(assertEquals(_, maybeSystemProperty.orElse(maybeEnvVar).map(_.value)))
        }
    }
  }

}

object XRayEnvironmentSuite {
  case class TraceId(value: String)
  object TraceId {
    val genTraceId: Gen[TraceId] =
      Gen.identifier.map(TraceId(_))
    implicit val arbTraceId: Arbitrary[TraceId] = Arbitrary(genTraceId)
  }
}
