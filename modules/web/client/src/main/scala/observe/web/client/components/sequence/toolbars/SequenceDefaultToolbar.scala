// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.toolbars

import diode.react.ReactConnectProxy
import japgolly.scalajs.react.React
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import observe.model.Observation
import observe.web.client.circuit._
import observe.web.client.components.ObserveStyles

final case class SequenceDefaultToolbar(id: Observation.Id)
    extends ReactProps[SequenceDefaultToolbar](SequenceDefaultToolbar.component) {

  val observerReader: ReactConnectProxy[Option[SequenceInfoFocus]]   =
    ObserveCircuit.connect(ObserveCircuit.sequenceObserverReader(id))
  val controlReader: ReactConnectProxy[Option[SequenceControlFocus]] =
    ObserveCircuit.connect(ObserveCircuit.sequenceControlReader(id))
}

/**
 * Toolbar for logged in users
 */
object SequenceDefaultToolbar {

  type Props = SequenceDefaultToolbar

  private val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P(p =>
      <.div(
        ObserveStyles.SequencesControl,
        <.div(ObserveStyles.SequenceControlButtons)(
          p.controlReader(_() match {
            case Some(c) => SequenceControl(c)
            case _       => React.Fragment()
          })
        ),
        <.div(ObserveStyles.SequenceInfo)(
          p.observerReader(_() match {
            case Some(p) => SequenceInfo(p)
            case _       => React.Fragment()
          })
        )
      )
    )
    .build

}
