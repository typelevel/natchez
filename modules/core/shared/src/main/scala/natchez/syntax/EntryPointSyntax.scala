// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.syntax

import cats.effect.MonadCancel
import cats.~>
import natchez.EntryPoint

final class EntryPointOps[F[_]](
    ep: EntryPoint[F]
) {

  def mapK[G[_]](
      f: F ~> G
  )(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): EntryPoint[G] =
    EntryPoint.mapK(ep, f)


}

trait EntryPointSyntax {

  implicit def entryPointOps[F[_]](
      ep: EntryPoint[F]
  ): EntryPointOps[F] = new EntryPointOps[F](ep)

}

