// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.data.Chain
import natchez.Span.Options.SpanCreationPolicy
import natchez.Span.SpanKind
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.typelevel.ci.CIString
import org.typelevel.ci.testing.arbitraries.*

trait Arbitraries {
  implicit val arbKernel: Arbitrary[Kernel] = Arbitrary {
    arbitrary[Map[CIString, String]].map(Kernel(_))
  }

  implicit val cogenKernel: Cogen[Kernel] =
    Cogen[Map[CIString, String]].contramap(_.toHeaders)

  implicit val arbKernelToKernel: Arbitrary[Kernel => Kernel] = Arbitrary {
    arbitrary[Map[CIString, String] => Map[CIString, String]].map {
      (f: Map[CIString, String] => Map[CIString, String]) =>
        f.compose[Kernel](_.toHeaders).andThen(Kernel(_))
    }
  }

  implicit val arbTraceValue: Arbitrary[TraceValue] = Arbitrary {
    Gen.oneOf(
      arbitrary[String].map(TraceValue.StringValue(_)),
      arbitrary[Boolean].map(TraceValue.BooleanValue(_)),
      arbitrary[Number].map(TraceValue.NumberValue(_))
    )
  }

  implicit val arbAttribute: Arbitrary[(String, TraceValue)] = Arbitrary {
    for {
      key <- arbitrary[String]
      value <- arbitrary[TraceValue]
    } yield key -> value
  }

  implicit val arbSpanCreationPolicy: Arbitrary[SpanCreationPolicy] = Arbitrary {
    Gen.oneOf(SpanCreationPolicy.Default, SpanCreationPolicy.Coalesce, SpanCreationPolicy.Suppress)
  }

  implicit val arbSpanKind: Arbitrary[SpanKind] = Arbitrary {
    Gen.oneOf(
      SpanKind.Internal,
      SpanKind.Client,
      SpanKind.Server,
      SpanKind.Producer,
      SpanKind.Consumer
    )
  }

  implicit val arbSpanOptions: Arbitrary[Span.Options] = Arbitrary {
    for {
      parentKernel <- arbitrary[Option[Kernel]]
      spanCreationPolicy <- arbitrary[SpanCreationPolicy]
      spanKind <- arbitrary[SpanKind]
      links <- arbitrary[List[Kernel]].map(Chain.fromSeq)
    } yield links.foldLeft {
      parentKernel.foldLeft {
        Span.Options.Defaults
          .withSpanKind(spanKind)
          .withSpanCreationPolicy(spanCreationPolicy)
      }(_.withParentKernel(_))
    }(_.withLink(_))
  }

}
