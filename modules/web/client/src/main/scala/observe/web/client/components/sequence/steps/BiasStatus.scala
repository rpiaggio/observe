// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.steps

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import observe.model.Observation
import observe.model.ObservationProgress
import observe.model.StepId
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.components.ObserveStyles
import observe.web.client.reusability._

case class BiasStatus(
  obsId:    Observation.Id,
  stepId:   StepId,
  fileId:   String,
  stopping: Boolean,
  paused:   Boolean
) extends ReactProps[BiasStatus](BiasStatus.component) {

  protected[steps] val connect =
    ObserveCircuit.connect(ObserveCircuit.obsProgressReader[ObservationProgress](obsId, stepId))
}

object BiasStatus extends ProgressLabel {
  type Props = BiasStatus

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]

  protected val component = ScalaComponent
    .builder[Props]("BiasStatus")
    .stateless
    .render_P(p =>
      <.div(
        ObserveStyles.specialStateLabel,
        p.connect(proxy =>
          <.span(
            proxy() match {
              case Some(ObservationProgress(_, _, _, _, stage)) =>
                label(p.fileId, None, p.stopping, p.paused, stage)
              case _                                            =>
                if (p.paused) s"${p.fileId} - Paused" else p.fileId
            }
          )
        )
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build
}
