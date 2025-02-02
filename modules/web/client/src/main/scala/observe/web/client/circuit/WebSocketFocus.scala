// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import scala.collection.immutable.SortedMap

import cats._
import cats.Order._
import lucuma.core.enums.Site
import monocle.Lens
import monocle.macros.Lenses
import observe.model.Observation
import observe.model._
import observe.model.enum.Resource
import observe.web.client.model.AlignAndCalibStep
import observe.web.client.model.Pages
import observe.web.client.model.ResourceRunOperation
import observe.web.client.model.ObserveAppRootModel
import observe.web.client.model.SequenceTab
import observe.web.client.model.SequencesOnDisplay
import observe.web.client.model.SoundSelection

@Lenses
final case class WebSocketsFocus(
  location:             Pages.ObservePages,
  sequences:            SequencesQueue[SequenceView],
  resourceRunRequested: Map[Observation.Id, SortedMap[Resource, ResourceRunOperation]],
  user:                 Option[UserDetails],
  displayNames:         Map[String, String],
  clientId:             Option[ClientId],
  site:                 Option[Site],
  sound:                SoundSelection,
  serverVersion:        Option[String],
  guideConfig:          TelescopeGuideConfig,
  alignAndCalib:        AlignAndCalibStep
)

object WebSocketsFocus {
  implicit val eq: Eq[WebSocketsFocus] =
    Eq.by(x =>
      (x.location,
       x.sequences,
       x.user,
       x.displayNames,
       x.clientId,
       x.site,
       x.serverVersion,
       x.guideConfig,
       x.alignAndCalib
      )
    )

  val webSocketFocusL: Lens[ObserveAppRootModel, WebSocketsFocus] =
    Lens[ObserveAppRootModel, WebSocketsFocus](m =>
      WebSocketsFocus(
        m.uiModel.navLocation,
        m.sequences,
        ObserveAppRootModel.sequenceTabsT
          .getAll(m)
          .map(t => t.obsIdName.id -> t.tabOperations.resourceRunRequested)
          .toMap,
        m.uiModel.user,
        m.uiModel.displayNames,
        m.clientId,
        m.site,
        m.uiModel.sound,
        m.serverVersion,
        m.guideConfig,
        m.alignAndCalib
      )
    )(v =>
      m =>
        m.copy(
          sequences = v.sequences,
          uiModel = m.uiModel.copy(
            user = v.user,
            sequencesOnDisplay = SequencesOnDisplay.sequenceTabs.modify(seqTab =>
              SequenceTab.resourcesRunOperationsL.replace(
                v.resourceRunRequested
                  .getOrElse(seqTab.obsIdName.id, SortedMap.empty)
              )(seqTab)
            )(m.uiModel.sequencesOnDisplay),
            displayNames = v.displayNames,
            sound = v.sound
          ),
          clientId = v.clientId,
          site = v.site,
          serverVersion = v.serverVersion,
          guideConfig = v.guideConfig,
          alignAndCalib = v.alignAndCalib
        )
    )
}
