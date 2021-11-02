// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import scala.concurrent.duration._
import cats.Eq
import cats.syntax.all._
import japgolly.scalajs.react.ReactCats._
import japgolly.scalajs.react.ReactMonocle._
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.extra.TimerSupport
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.util.Display
import lucuma.ui.forms._
import lucuma.ui.optics.InputFormat
import monocle.macros.Lenses
import observe.web.client.circuit.ObserveCircuit
import react.common._
import react.semanticui.collections.form._
import react.semanticui.elements.segment.Segment
import react.semanticui.widths._
import observe.model.Observer
import observe.model.Operator
import observe.model.enum.CloudCover
import observe.model.enum.ImageQuality
import observe.model.enum.SkyBackground
import observe.model.enum.WaterVapor
import observe.web.client.actions._
import observe.web.client.circuit._
import observe.web.client.components.forms.FormLabel
import observe.web.client.reusability._

/**
 * Container for a table with the steps
 */
final case class HeadersSideBar(model: HeaderSideBarFocus)
    extends ReactProps[HeadersSideBar](HeadersSideBar.component) {

  def canOperate: Boolean = model.status.canOperate

  // def selectedObserver: Either[Observer, Either[DayCalObserverFocus, SequenceObserverFocus]] =
  //   model.observer
}

/**
 * Display to show headers per sequence
 */
object HeadersSideBar {
  implicit val eqHeadersSideBar: Eq[HeadersSideBar]    = Eq.by(_.model)
  implicit val propsReuse: Reusability[HeadersSideBar] = Reusability.byEq

  private def conditionIntToString(v: Int): String = if (v === 100) "Any" else v.toString

  implicit val showSkyBackground: Display[SkyBackground] =
    Display.by(_.toInt.map(conditionIntToString).getOrElse("Unknown"), _.label)

  implicit val displayWaterVapor: Display[WaterVapor] =
    Display.by(_.toInt.map(conditionIntToString).getOrElse("Unknown"), _.label)

  implicit val showCloudCover: Display[CloudCover] =
    Display.by(_.toInt.map(conditionIntToString).getOrElse("Unknown"), _.label)

  implicit val showImageQuality: Display[ImageQuality] =
    Display.by(_.toInt.map(conditionIntToString).getOrElse("Unknown"), _.label)

  @Lenses
  final case class State(
    operator:     Option[Operator],
    prevOperator: Option[Operator]
  )

  object State {
    def apply(operator: Option[Operator]): State =
      State(operator, operator)

    implicit val stateEquals: Eq[State] = Eq.by(_.operator)

    implicit val stateReuse: Reusability[State] = Reusability.by(_.operator)
  }

  class Backend(val $ : BackendScope[HeadersSideBar, State]) extends TimerSupport {
    private def updateOperator(name: Operator): Callback =
      $.props >>= { p => ObserveCircuit.dispatchCB(UpdateOperator(name)).when_(p.canOperate) }

    def updateStateOp(value: Option[Operator], cb: Callback = Callback.empty): Callback =
      $.setStateL(State.operator)(value) >> cb

    def setupTimer: Callback =
      // Every 2 seconds check if the field has changed and submit
      // setInterval(submitIfChangedOp *> submitIfChangedOb, 2.second)
      setInterval(submitIfChangedOp, 2.second)

    def submitIfChangedOp: Callback =
      ($.state.zip($.props)) >>= { case (s, p) =>
        // println(s"OOOOOP ${s.operator}")
        s.operator
          .map(updateOperator)
          .getOrEmpty
          .when_(p.model.operator =!= s.operator)
      }

    def iqChanged(iq: ImageQuality): Callback =
      ObserveCircuit.dispatchCB(UpdateImageQuality(iq))

    def ccChanged(i: CloudCover): Callback =
      ObserveCircuit.dispatchCB(UpdateCloudCover(i))

    def sbChanged(sb: SkyBackground): Callback =
      ObserveCircuit.dispatchCB(UpdateSkyBackground(sb))

    def wvChanged(wv: WaterVapor): Callback =
      ObserveCircuit.dispatchCB(UpdateWaterVapor(wv))

    def render(p: HeadersSideBar, s: State): VdomNode = {
      val enabled    = p.model.status.canOperate
      val operatorEV =
        StateSnapshot[Operator](s.operator.getOrElse(Operator.Zero))(updateStateOp)
      Segment(secondary = true, clazz = ObserveStyles.headerSideBarStyle)(
        Form()(
          FormGroup(widths = Two, clazz = ObserveStyles.fieldsNoBottom)(
            <.div(
              ^.cls := "sixteen wide field",
              FormLabel("My display name", Some("displayName")),
              InputEV[StateSnapshot, Operator](
                "operator",
                "operator",
                operatorEV,
                format = InputFormat.fromIso(Operator.valueI.reverse),
                placeholder = "Display name...",
                disabled = !enabled,
                onBlur = _ => submitIfChangedOp
              )
            )
          ),
          FormGroup(widths = Two, clazz = SeqexecStyles.fieldsNoBottom)(
            <.div(
              ^.cls := "sixteen wide field",
              FormLabel("Operator", Some("operator")),
              InputEV[StateSnapshot, Operator](
                "operator",
                "operator",
                operatorEV,
                format = InputFormat.fromIso(Operator.valueI.reverse),
                placeholder = "Operator...",
                disabled = !enabled,
                onBlur = _ => submitIfChangedOp
              )
            )
          ),
          FormGroup(widths = Two, clazz = ObserveStyles.fieldsNoBottom)(
            EnumSelect[ImageQuality]("Image Quality",
                                     p.model.conditions.iq.some,
                                     "Select",
                                     disabled = !enabled,
                                     iqChanged
            ),
            EnumSelect[CloudCover]("Cloud Cover",
                                   p.model.conditions.cc.some,
                                   "Select",
                                   disabled = !enabled,
                                   ccChanged
            )
          ),
          FormGroup(widths = Two, clazz = ObserveStyles.fieldsNoBottom)(
            EnumSelect[WaterVapor]("Water Vapor",
                                   p.model.conditions.wv.some,
                                   "Select",
                                   disabled = !enabled,
                                   wvChanged
            ),
            EnumSelect[SkyBackground]("Sky Background",
                                      p.model.conditions.sb.some,
                                      "Select",
                                      disabled = !enabled,
                                      sbChanged
            )
          )
        )
      )
    }
  }

  private val component = ScalaComponent
    .builder[HeadersSideBar]
    .getDerivedStateFromPropsAndState[State] { (p, sOpt) =>
      val operator = p.model.operator

      sOpt.fold(State(operator)) { s =>
        Function.chain(
          List(
            State.operator.replace(operator),
            State.prevOperator.replace(operator)
          ).some
            .filter(_ => (operator =!= s.prevOperator) && operator.nonEmpty)
            .orEmpty
        )(s)
      }
    }
    .renderBackend[Backend]
    .configure(TimerSupport.install)
    .componentDidMount(_.backend.setupTimer)
    .configure(Reusability.shouldComponentUpdateAndLog("AAAA"))
    .build

}
