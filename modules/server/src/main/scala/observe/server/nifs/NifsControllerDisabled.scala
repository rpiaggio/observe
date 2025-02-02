// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.nifs

import cats.Applicative
import cats.syntax.all._
import fs2.Stream
import org.typelevel.log4cats.Logger
import observe.model.dhs.ImageFileId
import observe.model.`enum`.ObserveCommandResult
import observe.server.Progress
import observe.server.overrideLogMessage
import squants.Time

class NifsControllerDisabled[F[_]: Logger: Applicative] extends NifsController[F] {
  private val name = "NIFS"

  override def applyConfig(config: NifsController.NifsConfig): F[Unit] =
    overrideLogMessage(name, "applyConfig")

  override def observe(fileId: ImageFileId, cfg: NifsController.DCConfig): F[ObserveCommandResult] =
    overrideLogMessage(name, "").as(ObserveCommandResult.Success)

  override def endObserve: F[Unit] = overrideLogMessage(name, "endObserve")

  override def stopObserve: F[Unit] = overrideLogMessage(name, "stopObserve")

  override def abortObserve: F[Unit] = overrideLogMessage(name, "abortObserve")

  override def observeProgress(total: Time): Stream[F, Progress] = Stream.empty

  override def calcTotalExposureTime(cfg: NifsController.DCConfig): Time =
    NifsController.calcTotalExposureTime[F](cfg)
}
