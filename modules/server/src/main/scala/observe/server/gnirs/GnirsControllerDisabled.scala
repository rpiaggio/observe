// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gnirs

import cats.Applicative
import cats.syntax.all._
import fs2.Stream
import org.typelevel.log4cats.Logger
import observe.model.dhs.ImageFileId
import observe.model.`enum`.ObserveCommandResult
import observe.server.Progress
import observe.server.overrideLogMessage
import squants.Time

class GnirsControllerDisabled[F[_]: Logger: Applicative] extends GnirsController[F] {
  private val name = "GNIRS"

  override def applyConfig(config: GnirsController.GnirsConfig): F[Unit] =
    overrideLogMessage(name, "applyConfig")

  override def observe(fileId: ImageFileId, expTime: Time): F[ObserveCommandResult] =
    overrideLogMessage(name, s"observe $fileId").as(ObserveCommandResult.Success)

  override def endObserve: F[Unit] = overrideLogMessage(name, "endObserve")

  override def stopObserve: F[Unit] = overrideLogMessage(name, "stopObserve")

  override def abortObserve: F[Unit] = overrideLogMessage(name, "abortObserve")

  override def observeProgress(total: Time): Stream[F, Progress] = Stream.empty

  override def calcTotalExposureTime(cfg: GnirsController.DCConfig): F[Time] =
    GnirsController.calcTotalExposureTime[F](cfg)
}
