// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import observe.model.Observation
import observe.model.dhs.ImageFileId

/**
 * Header implementations know what headers sent before and after an observation
 */
trait Header[F[_]] {
  def sendBefore(obsId: Observation.Id, id: ImageFileId): F[Unit]
  def sendAfter(id:     ImageFileId): F[Unit]
}
