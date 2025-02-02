// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.queue

import scala.concurrent.duration._
import scala.math.max
import scala.scalajs.js
import cats.Eq
import cats.data.NonEmptyList
import cats.syntax.all._
import eu.timepit.refined.types.all.PosLong
import japgolly.scalajs.react.BackendScope
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.CallbackTo
import japgolly.scalajs.react.ReactMonocle._
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.TimerSupport
import japgolly.scalajs.react.facade.JsNumber
import japgolly.scalajs.react.vdom.html_<^._
import monocle.Lens
import monocle.macros.Lenses
import react.common._
import react.common.implicits._
import react.semanticui.colors._
import react.semanticui.elements.button.Button
import react.semanticui.sizes._
import react.sortable._
import react.virtualized._
import observe.model.Observation
import observe.model.QueueId
import observe.model.QueueManipulationOp
import observe.model.SequenceState
import observe.model.enum.Instrument
import observe.web.client.actions.ClearLastQueueOp
import observe.web.client.actions.RequestMoveCal
import observe.web.client.actions.RequestRemoveSeqCal
import observe.web.client.actions.UpdateCalTableState
import observe.web.client.circuit._
import observe.web.client.components.ObserveStyles
import observe.web.client.components.TableContainer
import observe.web.client.icons._
import observe.web.client.model.QueueSeqOperations
import observe.web.client.model.RemoveSeqQueue
import observe.web.client.reusability._
import web.client.table._

// ScalaJS defined trait
trait CalQueueRow extends js.Object {
  var obsIdName: Observation.IdName
  var instrument: Instrument
  var status: SequenceState
}

object CalQueueRow {

  def apply(
    obsIdName:  Observation.IdName,
    instrument: Instrument,
    status:     SequenceState
  ): CalQueueRow = {
    val p = (new js.Object).asInstanceOf[CalQueueRow]
    p.obsIdName = obsIdName
    p.instrument = instrument
    p.status = status
    p
  }

  def unapply(l: CalQueueRow): Option[(Observation.IdName, Instrument, SequenceState)] =
    Some((l.obsIdName, l.instrument, l.status))

  def Empty: CalQueueRow =
    apply(
      Observation.IdName(lucuma.core.model.Observation.Id(PosLong.MaxValue), "Default-1"),
      Instrument.F2,
      SequenceState.Idle
    )
}

final case class CalQueueTable(queueId: QueueId, data: CalQueueFocus)
    extends ReactProps[CalQueueTable](CalQueueTable.component) {
  val rowCount: Int = data.seqs.size

  val canOperate: Boolean = data.status.canOperate

  def moveSeq[T: Eq](list: List[T], i: Int, value: T): List[T] = {
    val (front, back) = list.splitAt(i)
    front.filterNot(_ === value) ++ List(value) ++ back.filterNot(_ === value)
  }

  def rowGetter(s: CalQueueTable.State)(i: Int): CalQueueRow = {
    val moved = s.moved
      .flatMap(c => data.seqs.lift(c._2.oldIndex).map(o => moveSeq(data.seqs, c._2.newIndex, o)))
      .getOrElse(data.seqs)
    moved
      .lift(i)
      .map(s => CalQueueRow(s.idName, s.i, s.status))
      .getOrElse(CalQueueRow.Empty)
  }

  def seqState(id: Observation.Id): Option[QueueSeqOperations] =
    CalQueueFocus.seqQueueOpsT(id).headOption(data)

  val clearOp: Boolean = data.lastOp match {
    case Some(QueueManipulationOp.Clear(_)) => true
    case _                                  => false
  }

  /**
   * Rows added on the last operation
   */
  val addedRows: List[Observation.Id] = data.lastOp match {
    case Some(QueueManipulationOp.AddedSeqs(_, x)) => x
    case _                                         => Nil
  }

  /**
   * Rows deleted on the last operation
   */
  val removedRows: List[Int] = data.lastOp match {
    case Some(QueueManipulationOp.RemovedSeqs(_, _, i)) => i
    case _                                              => Nil
  }

  /**
   * Rows deleted on the last operation
   */
  val movedRows: List[Observation.Id] = data.lastOp match {
    case Some(QueueManipulationOp.Moved(_, _, o, _)) => List(o)
    case _                                           => Nil
  }

  val afterDeletedRows: List[Int] =
    data.seqs.zipWithIndex
      .find { case (_, i) =>
        removedRows.contains(i)
      }
      .map(i => (i._2 to rowCount).toList)
      .orEmpty

  val upLifted: List[Int] =
    data.seqs.zipWithIndex
      .find { case (s, _) =>
        seqState(s.idName.id).exists(_.removeSeqQueue === RemoveSeqQueue.RemoveSeqQueueInFlight)
      }
      .map(i => ((i._2 + 1) to rowCount).toList)
      .orEmpty
}

/**
 * Calibration queue table
 */
object CalQueueTable {
  type Props = CalQueueTable

  sealed trait TableColumn     extends Product with Serializable
  case object RemoveSeqColumn  extends TableColumn
  case object StateSeqColumn   extends TableColumn
  case object ObsIdColumn      extends TableColumn
  case object InstrumentColumn extends TableColumn

  private val RemoveColumnWidth  = 30.0
  private val StateColumnWidth   = 25.0
  private val ObsIdMinWidth      = 66.2167 + ObserveStyles.TableBorderWidth
  private val InstrumentMinWidth = 90.4333 + ObserveStyles.TableBorderWidth

  object TableColumn {
    implicit val equal: Eq[TableColumn] = Eq.fromUniversalEquals

    implicit val reuse: Reusability[TableColumn] = Reusability.byRef
  }

  val RemoveSeqMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    RemoveSeqColumn,
    name = "remove",
    label = "",
    visible = true,
    FixedColumnWidth.unsafeFromDouble(RemoveColumnWidth)
  )

  val StateSeqMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    StateSeqColumn,
    name = "state",
    label = "",
    visible = true,
    FixedColumnWidth.unsafeFromDouble(StateColumnWidth)
  )

  val ObsIdColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ObsIdColumn,
    name = "obsid",
    label = "Obs. ID",
    visible = true,
    VariableColumnWidth.unsafeFromDouble(0.5, ObsIdMinWidth)
  )

  val InstrumentColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    InstrumentColumn,
    name = "instrument",
    label = "Instrument",
    visible = true,
    VariableColumnWidth.unsafeFromDouble(0.5, InstrumentMinWidth)
  )

  val all: NonEmptyList[ColumnMeta[TableColumn]] =
    NonEmptyList.of(RemoveSeqMeta, StateSeqMeta, ObsIdColumnMeta, InstrumentColumnMeta)

  val ro: NonEmptyList[ColumnMeta[TableColumn]] =
    NonEmptyList.of(StateSeqMeta, ObsIdColumnMeta, InstrumentColumnMeta)

  @Lenses
  final case class State(
    tableState:        TableState[TableColumn],
    animationRendered: Boolean,
    moved:             Option[(Observation.Id, IndexChange)],
    prevLastOp:        Option[QueueManipulationOp]
  )

  object State {
    val EditableTableState: TableState[TableColumn] =
      TableState(NotModified, 0, all)
    val ROTableState: TableState[TableColumn]       =
      TableState(NotModified, 0, ro)
    val DefaultRO: State                            =
      State(ROTableState, animationRendered = false, moved = None, prevLastOp = None)
    val DefaultEditable: State                      =
      State(EditableTableState, animationRendered = false, moved = None, prevLastOp = None)

    val scrollPosition: Lens[State, JsNumber] =
      State.tableState.andThen(TableState.scrollPosition)

    val columns: Lens[State, NonEmptyList[ColumnMeta[TableColumn]]] =
      State.tableState.andThen(TableState.columns)
  }

  implicit val propsReuse: Reusability[Props]    = Reusability.derive[Props]
  implicit val icReuse: Reusability[IndexChange] =
    Reusability.derive[IndexChange]
  implicit val stateReuse: Reusability[State]    =
    Reusability.by(x => (x.tableState, x.moved))

  val obsIdRenderer: CellRenderer[js.Object, js.Object, CalQueueRow] =
    (_, _, _, r: CalQueueRow, _) =>
      <.p(ObserveStyles.queueText |+| ObserveStyles.noselect, r.obsIdName.name)

  val instrumentRenderer: CellRenderer[js.Object, js.Object, CalQueueRow] =
    (_, _, _, r: CalQueueRow, _) =>
      <.p(ObserveStyles.queueText |+| ObserveStyles.noselect, r.instrument.show)

  private def removeSeq(qid: QueueId, sid: Observation.IdName): Callback =
    ObserveCircuit.dispatchCB(RequestRemoveSeqCal(qid, sid))

  def removeSeqRenderer(p: Props): CellRenderer[js.Object, js.Object, CalQueueRow] =
    (_, _, _, r: CalQueueRow, _) =>
      <.div(
        ObserveStyles.centeredCell,
        ObserveStyles.fullCell,
        Button(
          size = Mini,
          basic = false,
          color = Brown,
          disabled = !p.data.canOperate,
          compact = true,
          onClick = removeSeq(p.queueId, r.obsIdName),
          clazz = ObserveStyles.autoMargin,
          icon = true
        )(
          p.seqState(r.obsIdName.id)
            .filter(_.removeSeqQueue === RemoveSeqQueue.RemoveSeqQueueInFlight)
            .fold(IconTimes(^.onClick --> removeSeq(p.queueId, r.obsIdName)))(_ =>
              IconRefresh.loading(true)
            )
        )
      )

  private def statusIconRenderer: CellRenderer[js.Object, js.Object, CalQueueRow] =
    (_, _, _, row: CalQueueRow, _) => {
      val selectedIconStyle = ObserveStyles.selectedIcon
      val icon: TagMod      =
        row.status match {
          case SequenceState.Completed     =>
            IconCheckmark.clazz(selectedIconStyle)
          case SequenceState.Running(_, _) =>
            IconCircleNotched.copy(fitted = true, loading = true, clazz = ObserveStyles.runningIcon)
          case SequenceState.Failed(_)     =>
            IconAttention.copy(color = Red, clazz = selectedIconStyle)
          case _                           =>
            EmptyVdom
        }

      <.div(
        ObserveStyles.centeredCell,
        ObserveStyles.fullCell,
        icon
      )
    }

  class CalQueueTableBackend(b: BackendScope[Props, State]) extends TimerSupport {

    private def colBuilder(
      props: Props,
      state: State,
      size:  Size
    ): ColumnRenderArgs[TableColumn] => Table.ColumnArg = tb => {
      def updateState(s: TableState[TableColumn]): Callback =
        b.setStateL(State.tableState)(s) *>
          ObserveCircuit.dispatchCB(UpdateCalTableState(props.queueId, s))

      def renderer(c: TableColumn) = c match {
        case RemoveSeqColumn  => removeSeqRenderer(props)
        case StateSeqColumn   => statusIconRenderer
        case ObsIdColumn      => obsIdRenderer
        case InstrumentColumn => instrumentRenderer
      }

      tb match {
        case ColumnRenderArgs(meta, _, width, true)  =>
          Column(
            Column.propsNoFlex(
              width = width,
              dataKey = meta.name,
              label = meta.label,
              cellRenderer = renderer(meta.column),
              headerRenderer = resizableHeaderRenderer(
                state.tableState.resizeColumn(meta.column, size, updateState)
              ),
              className = ObserveStyles.queueTextColumn.htmlClass
            )
          )
        case ColumnRenderArgs(meta, _, width, false) =>
          Column(
            Column.propsNoFlex(
              width = width,
              dataKey = meta.name,
              label = meta.label,
              cellRenderer = renderer(meta.column),
              className =
                if (meta.column === InstrumentColumn)
                  ObserveStyles.queueTextColumn.htmlClass
                else ""
            )
          )
      }
    }

    private def rowStatusStyle(p: Props, s: State)(i: Int): Css =
      (i, p.rowGetter(s)(i)) match {
        case (-1, _)                                                     =>
          ObserveStyles.rowNone
        case (_, r: CalQueueRow) if r.status === SequenceState.Completed =>
          ObserveStyles.rowPositive
        case (_, r: CalQueueRow) if r.status.isRunning                   =>
          ObserveStyles.rowWarning
        case (_, r: CalQueueRow) if r.status.isError                     =>
          ObserveStyles.rowNegative
        case _                                                           =>
          ObserveStyles.rowNone
      }

    def rowClassName(p: Props, s: State)(i: Int): String =
      (((i, p.rowGetter(s)(i)) match {
        case (-1, _)                                                                             =>
          ObserveStyles.headerRowStyle
        case (_, CalQueueRow(i, _, _)) if p.addedRows.contains(i) && !s.animationRendered        =>
          ObserveStyles.stepRow |+| ObserveStyles.draggableRow |+| ObserveStyles.calRowBackground
        case (_, CalQueueRow(i, _, _)) if p.movedRows.contains(i.id) && !s.animationRendered     =>
          ObserveStyles.stepRow |+| ObserveStyles.draggableRow |+| ObserveStyles.calRowBackground
        case (i, CalQueueRow(_, _, _)) if p.afterDeletedRows.contains(i) && !s.animationRendered =>
          ObserveStyles.stepRow |+| ObserveStyles.draggableRow |+| ObserveStyles.calRowBackground
        case (i, _) if p.upLifted.contains(i) && !s.animationRendered                            =>
          ObserveStyles.stepRow |+| ObserveStyles.draggableRow |+| ObserveStyles.deletedRow
        case _                                                                                   =>
          ObserveStyles.stepRow |+| ObserveStyles.draggableRow
      }) |+| rowStatusStyle(p, s)(i)).htmlClass

    def updateScrollPosition(pos: JsNumber): Callback =
      b.props.zip(b.state) >>= { case (p, state) =>
        val s =
          State.scrollPosition.replace(pos)(state)
        b.setState(s) *>
          ObserveCircuit.dispatchCB(UpdateCalTableState(p.queueId, s.tableState))
      }

    private def collapsableStyle: (Int, Style) => Style = (_, s) => s

    def table(p: Props, s: State)(size: Size): Table.Props =
      Table.props(
        disableHeader = false,
        noRowsRenderer = () =>
          <.div(
            ^.cls    := "ui center aligned segment noRows",
            ObserveStyles.noRowsSegment,
            ^.height := size.height.toInt.px,
            "Cal queue empty"
          ),
        overscanRowCount = ObserveStyles.overscanRowCount,
        height = max(1, size.height.toInt),
        rowCount = p.rowCount,
        rowHeight = ObserveStyles.rowHeight,
        rowClassName = rowClassName(p, s) _,
        width = max(1, size.width.toInt),
        rowGetter = p.rowGetter(s) _,
        headerClassName = ObserveStyles.tableHeader.htmlClass,
        scrollTop = s.tableState.scrollPosition,
        onScroll = (_, _, pos) => updateScrollPosition(pos),
        rowRenderer = sortableRowRenderer(collapsableStyle),
        headerHeight = ObserveStyles.headerHeight,
        gridClassName =
          if (p.clearOp && !s.animationRendered)
            ObserveStyles.calTableBorder.htmlClass
          else ""
      )

    def requestMove(c: IndexChange): Callback =
      b.props >>= { p =>
        val movedObsId =
          p.data.seqs
            .map(_.idName)
            .lift(c.oldIndex)
        movedObsId
          .map(obsIdName =>
            ObserveCircuit.dispatchCB(
              RequestMoveCal(p.queueId, obsIdName, c.newIndex - c.oldIndex)
            ) >>
              b.setStateL(State.moved)((obsIdName.id, c).some)
          )
          .getOrEmpty
      }

    def resetAnim(p: Props): Callback =
      ObserveCircuit
        .dispatchCB(ClearLastQueueOp(p.queueId))
        .when(p.data.lastOp.isDefined)
        .void

    def render(p: Props, s: State): VdomElement =
      TableContainer(
        p.canOperate,
        size =>
          if (size.width.toInt > 0) {
            val sortableList =
              SortableContainer.wrapC(Table.component,
                                      s.tableState
                                        .columnBuilder(size, colBuilder(p, s, size))
                                        .map(_.vdomElement)
              )

            // If distance is 0 we can miss some events
            val cp = SortableContainer.Props(
              onSortEnd = requestMove,
              shouldCancelStart = _ => CallbackTo(!p.data.canOperate),
              helperClass = (ObserveStyles.noselect |+| ObserveStyles.draggedRowHelper).htmlClass,
              distance = 3
            )
            sortableList(cp)(table(p, s)(size))
          } else {
            <.div()
          },
        onResize = _ => Callback.empty
      )
  }

  def initialState(p: Props): State =
    if (p.data.loggedIn) {
      State.DefaultEditable
        .copy(tableState = p.data.tableState, prevLastOp = p.data.lastOp)
    } else {
      State.DefaultRO
        .copy(tableState = p.data.tableState, prevLastOp = p.data.lastOp)
    }

  private val component = ScalaComponent
    .builder[Props]
    .initialStateFromProps(initialState)
    .renderBackend[CalQueueTableBackend]
    .getDerivedStateFromProps { (props, state) =>
      // This is a bit tricky. if the last op has changed we allow animation
      val opChanged = props.data.lastOp =!= state.prevLastOp

      val animationRendered = !opChanged & state.animationRendered

      val moved = state.moved.filter { case (obsId, ic) =>
        props.data.seqs
          .map(_.idName.id)
          .lift(ic.newIndex)
          .forall(_ =!= obsId)
      }

      val cols = if (props.data.loggedIn) {
        State.DefaultEditable.tableState.columns
      } else {
        State.DefaultRO.tableState.columns
      }

      (
        State.animationRendered.replace(animationRendered) >>>
          State.moved.replace(moved) >>>
          State.columns.replace(cols) >>>
          State.prevLastOp.replace(props.data.lastOp)
      )(state)
    }
    .componentDidMount($ => $.backend.resetAnim($.props))
    .componentDidUpdate($ => $.backend.setTimeout($.backend.resetAnim($.currentProps), 1.second))
    .configure(Reusability.shouldComponentUpdate)
    .configure(TimerSupport.install)
    .build

  def apply(p: Props): Unmounted[Props, State, CalQueueTableBackend] =
    component(p)
}
