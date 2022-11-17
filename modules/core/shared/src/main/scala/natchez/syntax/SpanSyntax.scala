// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.syntax

import cats.effect.kernel.MonadCancel
import cats.~>
import natchez.Span

final class SpanOps[F[_]](
    span: Span[F]
) {

  def mapK[G[_]](f: F ~> G)(
      implicit F: MonadCancel[F, _],
      G: MonadCancel[G, _]
  ): Span[G] = Span.mapK[F, G](span, f)

}

trait SpanSyntax {

  implicit def spanOps[F[_]](
      span: Span[F]
  ): SpanOps[F] = new SpanOps[F](span)

}
