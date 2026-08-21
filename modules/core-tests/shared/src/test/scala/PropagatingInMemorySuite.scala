// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.effect.IO
import cats.syntax.all._
import munit.CatsEffectSuite

class PropagatingInMemorySuite extends CatsEffectSuite {

  // Every span exposes a distinct id via spanId, and its kernel advertises that same id.
  test("spanId is distinct per span and matches the id carried in the kernel") {
    PropagatingInMemory.EntryPoint.create[IO].flatMap { ep =>
      ep.root("root").use { root =>
        root.span("child").use { child =>
          for {
            rootId <- root.spanId
            childId <- child.spanId
            childKern <- child.kernel
          } yield {
            assert(rootId.isDefined)
            assertNotEquals(rootId, childId)
            assertEquals(childKern.toHeaders.get(PropagatingInMemory.SpanIdHeader), childId)
          }
        }
      }
    }
  }

  // A child created inside a span is parented to that enclosing span.
  test("child span's parentId is the enclosing span") {
    PropagatingInMemory.EntryPoint.create[IO].flatMap { ep =>
      ep.root("root").use { root =>
        root.span("child").use { _ =>
          ep.spans.map { spans =>
            val root = spans.find(_.name == "root")
            val child = spans.find(_.name == "child")
            assertEquals(child.flatMap(_.parentId), root.map(_.id))
          }
        }
      }
    }
  }

  // An explicit parentKernel wins over the enclosing span. This is the property a plain InMemory
  // tracer cannot model, and the one real backends implement.
  test("explicit parentKernel wins over the enclosing span") {
    PropagatingInMemory.EntryPoint.create[IO].flatMap { ep =>
      val run = ep.root("root").use { root =>
        // `origin` is the span whose kernel we propagate explicitly.
        root.span("origin").use(_.kernel).flatMap { originKernel =>
          // `nested` is created lexically inside `holder`, but is handed origin's kernel as parent.
          root.span("holder").use { holder =>
            holder.span("nested", Span.Options.parentKernel(originKernel)).use_
          }
        }
      }

      run *> ep.spans.map { spans =>
        val origin = spans.find(_.name == "origin")
        val holder = spans.find(_.name == "holder")
        val nested = spans.find(_.name == "nested")
        // nested's parent is origin (explicit kernel), not holder (its enclosing span).
        assertEquals(nested.flatMap(_.parentId), origin.map(_.id))
        assertNotEquals(nested.flatMap(_.parentId), holder.map(_.id))
      }
    }
  }

  // Concurrent children each keep their own parent: no id collision, no context leakage.
  test("concurrent children each parent to their own enclosing span") {
    PropagatingInMemory.EntryPoint.create[IO].flatMap { ep =>
      val n = 50
      val run = ep.root("root").use { root =>
        (1 to n).toList.parTraverse_ { i =>
          root.span(s"parent-$i").use { parent =>
            parent.span(s"child-$i").use_
          }
        }
      }

      run *> ep.spans.map { spans =>
        val byName = spans.groupBy(_.name).map { case (k, v) => k -> v.head }
        (1 to n).foreach { i =>
          val parent = byName.get(s"parent-$i")
          val child = byName.get(s"child-$i")
          assertEquals(child.flatMap(_.parentId), parent.map(_.id), s"child-$i")
        }
        // Every span got a distinct id.
        assertEquals(spans.map(_.id).toSet.size, spans.size)
      }
    }
  }
}
