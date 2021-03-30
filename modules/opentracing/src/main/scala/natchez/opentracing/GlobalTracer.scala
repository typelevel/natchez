// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package opentracing

import cats.effect.Sync
import cats.implicits._
import io.opentracing.util.{ GlobalTracer => GT }
import io.{opentracing => ot}

object GlobalTracer {

  def hasRegisteredTracer[F[_]: Sync]: F[Boolean] = Sync[F].delay(GT.isRegistered())

  def fetch[F[_]: Sync]: F[Option[ot.Tracer]] = 
    hasRegisteredTracer[F].flatMap {
      case true => Sync[F].delay(Some(GT.get()))
      case false => Sync[F].pure(None)
    }

  def registerTracer[F[_]: Sync](tracer: ot.Tracer): F[Boolean] = Sync[F].delay(GT.registerIfAbsent(tracer))
}
 

