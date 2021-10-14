// Copyright (c) 2019-2021 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.mtl.Local
import cats.effect._

package object mtl {
  implicit def natchezMtlTraceForLocal[F[_]](
    implicit ev: Local[F, Span[F]],
             eb: MonadCancel[F, Throwable],
  ): Trace[F] =
    new LocalTrace(ev)
}
