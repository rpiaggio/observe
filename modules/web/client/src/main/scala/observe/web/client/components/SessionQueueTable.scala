// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import scala.math.max
import scala.scalajs.js
import cats.Eq
import cats.data.NonEmptyList
import cats.syntax.all._
import eu.timepit.refined.types.numeric.PosLong
import japgolly.scalajs.react.ReactCats._
import japgolly.scalajs.react.ReactMonocle._
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.builder.Lifecycle.RenderScope
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.facade.JsNumber
import japgolly.scalajs.react.vdom.html_<^._
import monocle.Lens
import monocle.macros.Lenses
import react.common._
import react.common.implicits._
import react.semanticui.colors._
import react.semanticui.elements.icon.Icon
import react.semanticui.sizes._
import react.virtualized._
import observe.model.{
  CalibrationQueueId,
  Observation,
  Observer,
  RunningStep,
  SequenceState,
  StepId,
  UnknownTargetName,
  UserDetails
}
import observe.model.enum.Instrument
import observe.web.client.actions._
import observe.web.client.circuit._
import observe.web.client.icons._
import observe.web.client.model.ModelOps._
import observe.web.client.model.ObsClass
import observe.web.client.model.Pages._
import observe.web.client.model.SessionQueueFilter
import observe.web.client.reusability._
import web.client.table._
import web.client.JsNumberOps._

trait Columns {
  import SessionQueueTable._

  val IconColumnWidth       = 25.0
  val AddQueueColumnWidth   = 30.0
  val ClassColumnWidth      = 26.0
  val ObsIdColumnWidth      = 140.0
  val ObsIdMinWidth         = 72.2667 + ObserveStyles.TableBorderWidth // Measured valu e
  val StateColumnWidth      = 80.0
  val StateMinWidth         = 53.3667 + ObserveStyles.TableBorderWidth
  val InstrumentColumnWidth = 80.0
  val InstrumentMinWidth    = 90.4333 + ObserveStyles.TableBorderWidth
  val TargetNameColumnWidth = 140.0
  val TargetMinWidth        = 60.0167 + ObserveStyles.TableBorderWidth
  val ObsNameColumnWidth    = 100.0
  val ObsNameMinWidth       = 89.7340 + ObserveStyles.TableBorderWidth
  val ObserverColumnWidth   = 100.0
  val ObserverMinWidth      = 89.7340 + ObserveStyles.TableBorderWidth

  val IconColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    IconColumn,
    name = "status",
    label = "",
    visible = true,
    width = FixedColumnWidth.unsafeFromDouble(IconColumnWidth)
  )

  val ClassColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ClassColumn,
    name = "class",
    label = "",
    visible = true,
    width = FixedColumnWidth.unsafeFromDouble(ClassColumnWidth)
  )

  val AddQueueColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    AddQueueColumn,
    name = "",
    label = "",
    visible = true,
    width = FixedColumnWidth.unsafeFromDouble(AddQueueColumnWidth)
  )

  val ObsIdColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ObsIdColumn,
    name = "obsId",
    label = "Obs. ID",
    visible = true,
    width = VariableColumnWidth.unsafeFromDouble(0.2, ObsIdMinWidth)
  )

  val StateColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    StateColumn,
    name = "state",
    label = "State",
    visible = true,
    grow = 2,
    width = VariableColumnWidth.unsafeFromDouble(0.1, StateMinWidth)
  )

  val InstrumentColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    InstrumentColumn,
    name = "instrument",
    label = "Instrument",
    visible = true,
    removeable = 1,
    grow = 0,
    width = VariableColumnWidth.unsafeFromDouble(0.2, InstrumentMinWidth)
  )

  val TargetNameColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    TargetNameColumn,
    name = "target",
    label = "Target",
    visible = true,
    grow = 3,
    removeable = 2,
    width = VariableColumnWidth.unsafeFromDouble(0.25, TargetMinWidth)
  )

  val ObsNameColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ObsNameColumn,
    name = "obsName",
    label = "Obs. Name",
    visible = true,
    removeable = 3,
    grow = 3,
    width = VariableColumnWidth.unsafeFromDouble(0.25, ObsNameMinWidth)
  )

  val ObserverColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ObserverColumn,
    name = "observer",
    label = "Observer",
    visible = true,
    removeable = 3,
    grow = 3,
    width = VariableColumnWidth.unsafeFromDouble(0.25, ObserverMinWidth)
  )

  val all: NonEmptyList[ColumnMeta[TableColumn]] = NonEmptyList.of(
    IconColumnMeta,
    AddQueueColumnMeta,
    ClassColumnMeta,
    ObsIdColumnMeta,
    StateColumnMeta,
    InstrumentColumnMeta,
    TargetNameColumnMeta,
    ObsNameColumnMeta,
    ObserverColumnMeta
  )

  val allTC = all.map(_.column)

  val columnDefaultWidth: Map[TableColumn, Double] = Map(
    IconColumn       -> IconColumnWidth,
    AddQueueColumn   -> AddQueueColumnWidth,
    ClassColumn      -> ClassColumnWidth,
    ObsIdColumn      -> ObsIdColumnWidth,
    StateColumn      -> StateColumnWidth,
    InstrumentColumn -> InstrumentColumnWidth,
    TargetNameColumn -> TargetNameColumnWidth,
    ObsNameColumn    -> ObsNameColumnWidth,
    ObserverColumn   -> ObserverColumnWidth
  )

  val columnsMinWidth: Map[TableColumn, Double] = Map(
    ObsIdColumn      -> ObsIdMinWidth,
    StateColumn      -> StateMinWidth,
    InstrumentColumn -> InstrumentMinWidth,
    TargetNameColumn -> TargetMinWidth,
    ObsNameColumn    -> ObsNameMinWidth,
    ObserverColumn   -> ObserverMinWidth
  )

  def statusText(status: SequenceState, runningStep: Option[RunningStep]): String =
    s"${status.show} ${runningStep.map(u => s" ${u.show}").getOrElse("")}"

}

// ScalaJS defined trait
trait SessionQueueRow extends js.Object {
  var obsId: Observation.IdName
  var status: SequenceState
  var instrument: Instrument
  var targetName: Option[String]
  var observer: Option[Observer]
  var name: String
  var obsClass: ObsClass
  var active: Boolean
  var loaded: Boolean
  var nextStepToRun: Option[StepId]
  var runningStep: Option[RunningStep]
  var inDayCalQueue: Boolean
}

object SessionQueueRow {

  def apply(
    obsIdName:     Observation.IdName,
    status:        SequenceState,
    instrument:    Instrument,
    targetName:    Option[String],
    observer:      Option[Observer],
    name:          String,
    obsClass:      ObsClass,
    active:        Boolean,
    loaded:        Boolean,
    nextStepToRun: Option[StepId],
    runningStep:   Option[RunningStep],
    inDayCalQueue: Boolean
  ): SessionQueueRow = {
    val p = (new js.Object).asInstanceOf[SessionQueueRow]
    p.obsId = obsIdName
    p.status = status
    p.instrument = instrument
    p.targetName = targetName
    p.observer = observer
    p.name = name
    p.obsClass = obsClass
    p.active = active
    p.nextStepToRun = nextStepToRun
    p.runningStep = runningStep
    p.loaded = loaded
    p.inDayCalQueue = inDayCalQueue
    p
  }

  def unapply(l: SessionQueueRow): Option[
    (
      Observation.IdName,
      SequenceState,
      Instrument,
      Option[String],
      Option[Observer],
      String,
      ObsClass,
      Boolean,
      Boolean,
      Option[StepId],
      Option[RunningStep],
      Boolean
    )
  ] =
    Some(
      (l.obsId,
       l.status,
       l.instrument,
       l.targetName,
       l.observer,
       l.name,
       l.obsClass,
       l.active,
       l.loaded,
       l.nextStepToRun,
       l.runningStep,
       l.inDayCalQueue
      )
    )

  object Empty extends SessionQueueRow {
    override var obsId: Observation.IdName        =
      Observation.IdName(lucuma.core.model.Observation.Id(PosLong.MaxValue), "Zero-1")
    override var status: SequenceState            = SequenceState.Idle
    override var instrument: Instrument           = Instrument.F2
    override var targetName: Option[String]       = None
    override var observer: Option[Observer]       = None
    override var name                             = ""
    override var obsClass: ObsClass               = ObsClass.Nighttime
    override var active: Boolean                  = false
    override var loaded: Boolean                  = false
    override var nextStepToRun: Option[StepId]    = None
    override var runningStep: Option[RunningStep] = None
    override var inDayCalQueue: Boolean           = false
  }
}

final case class SessionQueueTable(
  ctl:       RouterCtl[ObservePages],
  sequences: StatusAndLoadedSequencesFocus
) extends ReactProps[SessionQueueTable](SessionQueueTable.component)
    with Columns {
  import SessionQueueTable._

  val sequencesList: List[SequenceInSessionQueue] =
    sequences.queueFilter.filter(sequences.sequences)

  val obsIds: List[Observation.Id] = sequencesList.map(_.idName.id)

  def rowGetter(i: Int): SessionQueueRow =
    sequencesList
      .lift(i)
      .map { s =>
        SessionQueueRow(s.idName,
                        s.status,
                        s.instrument,
                        s.targetName,
                        s.observer,
                        s.name,
                        s.obsClass,
                        s.active,
                        s.loaded,
                        s.nextStepToRun,
                        s.runningStep,
                        s.inDayCalQueue
        )
      }
      .getOrElse(SessionQueueRow.Empty)

  val rowCount: Int = sequencesList.size

  val canOperate: Boolean = sequences.status.canOperate

  val loggedIn: Boolean = sequences.status.isLogged

  val user: Option[UserDetails] = sequences.status.user

  val extractors = List[(TableColumn, SequenceInSessionQueue => String)](
    (ObsIdColumn, _.idName.name),
    (StateColumn, s => statusText(s.status, s.runningStep)),
    (InstrumentColumn, _.instrument.show),
    (TargetNameColumn, _.targetName.orEmpty),
    (ObsNameColumn, _.name)
  ).toMap

  private val columnAdjustmens =
    Map[TableColumn, Double](ObsIdColumn -> ObserveStyles.TableRightPadding.toDouble)

  val columnWidths: TableColumn => Option[Double] =
    colWidths(sequencesList, allTC, extractors, columnsMinWidth, columnAdjustmens)

  // Hide some columns depending on login state
  val visibleColumns: TableColumn => Boolean = {
    case ObsNameColumn | AddQueueColumn | TargetNameColumn =>
      loggedIn
    case _                                                 => true
  }

}

object SessionQueueTable extends Columns {
  type Backend = RenderScope[Props, State, Unit]
  type Props   = SessionQueueTable

  sealed trait TableColumn     extends Product with Serializable
  case object IconColumn       extends TableColumn
  case object AddQueueColumn   extends TableColumn
  case object ClassColumn      extends TableColumn
  case object ObsIdColumn      extends TableColumn
  case object StateColumn      extends TableColumn
  case object InstrumentColumn extends TableColumn
  case object ObsNameColumn    extends TableColumn
  case object TargetNameColumn extends TableColumn
  case object ObserverColumn   extends TableColumn

  object TableColumn {
    implicit val equal: Eq[TableColumn] = Eq.fromUniversalEquals

    implicit val tcReuse: Reusability[TableColumn] = Reusability.byRef
  }

  @Lenses
  final case class State(
    tableState:   TableState[TableColumn],
    rowLoading:   Option[Int],
    lastSize:     Option[Size],
    prevObsIds:   List[Observation.Id],
    prevLoggedIn: Boolean
  ) {
    // Reset loading of rows
    def resetLoading(p: Props): State =
      if (rowLoading.exists(i => p.rowGetter(i).loaded)) {
        copy(rowLoading = None)
      } else {
        this
      }
  }

  object State {
    // Lenses
    val columns: Lens[State, NonEmptyList[ColumnMeta[TableColumn]]] =
      tableState.andThen(TableState.columns[TableColumn])

    val userModified: Lens[State, UserModified] =
      tableState.andThen(TableState.userModified[TableColumn])

    val scrollPosition: Lens[State, JsNumber] =
      tableState.andThen(TableState.scrollPosition[TableColumn])

    val InitialTableState: TableState[TableColumn] =
      TableState(NotModified, 0, all)

    val InitialState: State =
      State(InitialTableState, None, None, List.empty, prevLoggedIn = false)
  }

  // Reusability
  implicit val sqSeFocusReuse: Reusability[SequenceInSessionQueue]        =
    Reusability.byEq
  implicit val qfReuse: Reusability[SessionQueueFilter]                   =
    Reusability.byEq
  implicit val stSeFocusReuse: Reusability[StatusAndLoadedSequencesFocus] =
    Reusability.by(x => (x.status, x.sequences, x.tableState, x.queueFilter))
  implicit val propsReuse: Reusability[Props]                             = Reusability.by(_.sequences)
  implicit val stateReuse: Reusability[State]                             =
    Reusability.by(s => (s.tableState, s.rowLoading, s.lastSize))

  private def linkTo(p: Props, page: ObservePages)(mod: TagMod*) =
    <.a(
      ^.href      := p.ctl.urlFor(page).value,
      ^.onClick ==> { _.preventDefaultCB },
      ^.draggable := false,
      mod.toTagMod
    )

  private def pageOf(row: SessionQueueRow): ObservePages =
    if (row.loaded) {
      SequencePage(row.instrument, row.obsId.id, StepIdDisplayed(row.nextStepToRun))
    } else {
      PreviewPage(row.instrument, row.obsId.id, StepIdDisplayed(row.nextStepToRun))
    }

  private def linkedTextRenderer(p: Props)(
    f:                              SessionQueueRow => String
  ): CellRenderer[js.Object, js.Object, SessionQueueRow] =
    (_, _, _, row: SessionQueueRow, _) =>
      linkTo(p, pageOf(row))(ObserveStyles.queueTextColumn, <.p(ObserveStyles.queueText, f(row)))

  private def statusIconRenderer(
    b: Backend
  ): CellRenderer[js.Object, js.Object, SessionQueueRow] =
    (_, _, _, row: SessionQueueRow, index) => {
      val isFocused         = row.active
      val selectedIconStyle = ObserveStyles.selectedIcon
      val icon: TagMod      =
        row.status match {
          case SequenceState.Completed                     =>
            Icon(name = "checkmark", clazz = selectedIconStyle)
          case SequenceState.Running(_, _)                 =>
            Icon(name = "circle notched",
                 fitted = true,
                 loading = true,
                 clazz = ObserveStyles.runningIcon
            )
          case SequenceState.Failed(_)                     =>
            Icon(name = "attention", color = Red, clazz = selectedIconStyle)
          case _ if b.state.rowLoading.exists(_ === index) =>
            // Spinning icon while loading
            IconRefresh.copy(fitted = true, loading = true, clazz = ObserveStyles.runningIcon)
          case _ if isFocused                              =>
            Icon(name = "dot circle outline", clazz = selectedIconStyle)
          case _                                           =>
            <.div()
        }

      linkTo(b.props, pageOf(row))(
        ObserveStyles.queueIconColumn,
        icon
      )
    }

  def addToQueueE(idName: Observation.IdName)(e: ReactEvent): Callback =
    e.stopPropagationCB *>
      e.preventDefaultCB *>
      ObserveCircuit.dispatchCB(RequestAddSeqCal(CalibrationQueueId, idName.id))

  def removeFromQueueE(idName: Observation.IdName)(e: ReactEvent): Callback =
    e.stopPropagationCB *>
      e.preventDefaultCB *>
      ObserveCircuit.dispatchCB(RequestRemoveSeqCal(CalibrationQueueId, idName))

  private def addToQueueRenderer(
    b: Backend
  ): CellRenderer[js.Object, js.Object, SessionQueueRow] =
    (_, _, _, row: SessionQueueRow, _) => {
      val title =
        if (row.inDayCalQueue) "Remove from daycal queue"
        else "Add to daycal queue"
      linkTo(b.props, pageOf(row))(
        ObserveStyles.queueIconColumn,
        ^.title := title,
        if (row.inDayCalQueue) {
          <.span(
            Icon(name = "check circle outline",
                 size = Large,
                 fitted = true,
                 clazz = ObserveStyles.selectedIcon
            ),
            ^.onClick ==> removeFromQueueE(row.obsId)
          )
        } else {
          <.span(
            Icon(name = "circle outline",
                 size = Large,
                 fitted = true,
                 clazz = ObserveStyles.selectedIcon
            ),
            ^.onClick ==> addToQueueE(row.obsId)
          )
        }
      )
    }

  private def classIconRenderer(
    b: Backend
  ): CellRenderer[js.Object, js.Object, SessionQueueRow] =
    (_, _, _, row: SessionQueueRow, _) => {
      val icon: TagMod =
        row.obsClass match {
          case ObsClass.Daytime   =>
            IconSun.clazz(ObserveStyles.selectedIcon)
          case ObsClass.Nighttime =>
            IconMoon.clazz(ObserveStyles.selectedIcon)
          case _                  =>
            <.div()
        }

      linkTo(b.props, pageOf(row))(
        ObserveStyles.queueIconColumn,
        icon
      )
    }

  private val statusHeaderRenderer: HeaderRenderer[js.Object] =
    (_, _, _, _, _, _) =>
      <.div(
        ^.title := "Control",
        ^.width := IconColumnWidth.px
      )

  private def addAll(): Callback =
    ObserveCircuit.dispatchCB(RequestAllSelectedSequences(CalibrationQueueId))

  private val addHeaderRenderer: HeaderRenderer[js.Object] =
    (_, _, _, _, _, _) =>
      <.div(
        ^.title := "Add all to queue",
        ^.width := (AddQueueColumnWidth - 1).px,
        ObserveStyles.selectedIcon,
        ObserveStyles.centeredCell,
        <.span(
          Icon(name = "calendar alternate outline", fitted = true, link = true),
          ^.onClick --> addAll()
        )
      )

  private val timeHeaderRenderer: HeaderRenderer[js.Object] =
    (_, _, _, _, _, _) =>
      <.div(
        ^.title := "Obs. class",
        ^.width := (ClassColumnWidth - 1).px,
        ObserveStyles.selectedIcon,
        ObserveStyles.centeredCell,
        Icon(name = "clock outline", fitted = true)
      )

  private val draggableRow =
    ObserveStyles.stepRow |+| ObserveStyles.draggableRow

  private def rowClassName(p: Props)(i: Int): String =
    ((i, p.rowGetter(i)) match {
      case (-1, _)                                                         =>
        ObserveStyles.headerRowStyle
      case (_, r: SessionQueueRow) if r.status === SequenceState.Completed =>
        draggableRow |+| ObserveStyles.rowPositive
      case (_, r: SessionQueueRow) if r.status.isRunning                   =>
        draggableRow |+| ObserveStyles.rowWarning
      case (_, r: SessionQueueRow) if r.status.isError                     =>
        draggableRow |+| ObserveStyles.rowNegative
      case (_, r: SessionQueueRow) if r.active && !r.status.isInProcess    =>
        draggableRow |+| ObserveStyles.rowActive
      case _                                                               =>
        draggableRow
    }).htmlClass

  private def renderer(c: TableColumn, b: Backend) = c match {
    case IconColumn       => statusIconRenderer(b)
    case AddQueueColumn   => addToQueueRenderer(b)
    case ClassColumn      => classIconRenderer(b)
    case ObsIdColumn      => linkedTextRenderer(b.props)(_.obsId.name)
    case StateColumn      => linkedTextRenderer(b.props)(r => statusText(r.status, r.runningStep))
    case InstrumentColumn => linkedTextRenderer(b.props)(_.instrument.show)
    case TargetNameColumn => linkedTextRenderer(b.props)(_.targetName.getOrElse(UnknownTargetName))
    case ObsNameColumn    => linkedTextRenderer(b.props)(_.name)
    case ObserverColumn   => linkedTextRenderer(b.props)(_.observer.foldMap(_.value))
  }

  private val fixedHeaderRenderer: TableColumn => HeaderRenderer[js.Object] = {
    case IconColumn     => statusHeaderRenderer
    case AddQueueColumn => addHeaderRenderer
    case ClassColumn    => timeHeaderRenderer
    case _              => defaultHeaderRendererS
  }

  private val columnStyle: TableColumn => Option[Css] = {
    case ObsIdColumn | StateColumn | InstrumentColumn | ObsNameColumn | TargetNameColumn =>
      ObserveStyles.queueTextColumn.some
    case _                                                                               => ObserveStyles.queueIconColumn.some
  }

  def updateScrollPosition(b: Backend, pos: JsNumber): Callback = {
    val mods = State.userModified.replace(IsModified) >>>
      State.scrollPosition.replace(pos)
    (b.modState(mods) *> ObserveCircuit.dispatchCB(
      UpdateSessionQueueTableState(mods(b.state).tableState)
    )).unless(pos === 0 && !b.state.tableState.isModified).void
  }

  private def colBuilder(
    b:    Backend,
    size: Size
  ): ColumnRenderArgs[TableColumn] => Table.ColumnArg = {
    case ColumnRenderArgs(meta, _, width, true) =>
      Column(
        Column.propsNoFlex(
          width = width,
          dataKey = meta.name,
          label = meta.label,
          cellRenderer = renderer(meta.column, b),
          headerRenderer = resizableHeaderRenderer(
            b.state.tableState.resizeColumn(
              meta.column,
              size,
              x =>
                b.setStateL(State.tableState)(x) *>
                  ObserveCircuit.dispatchCB(UpdateSessionQueueTableState(x)),
              b.props.visibleColumns,
              b.props.columnWidths
            )
          ),
          className = columnStyle(meta.column).foldMap(_.htmlClass)
        )
      )

    case ColumnRenderArgs(meta, _, width, false) =>
      Column(
        Column.propsNoFlex(
          width = width,
          dataKey = meta.name,
          label = meta.label,
          headerRenderer = fixedHeaderRenderer(meta.column),
          cellRenderer = renderer(meta.column, b),
          className = columnStyle(meta.column).foldMap(_.htmlClass)
        )
      )
  }

  // Single click puts in preview or go to tab
  def singleClick(b: Backend)(i: Int): Callback = {
    val r = b.props.rowGetter(i)
    val p = pageOf(r)
    // If already loaded switch tabs or to preview
    b.props.ctl.setUrlAndDispatchCB(p)
  }

  // Double click tries to load
  def doubleClick(b: Backend)(i: Int): Callback = {
    val r = b.props.rowGetter(i)
    if (r.loaded) {
      // If already loaded switch tabs
      b.props.ctl.dispatchAndSetUrlCB(
        SelectIdToDisplay(r.instrument, r.obsId.id, StepIdDisplayed(r.nextStepToRun))
      )
    } else { // Try to load it
      b.props.user
        .filter(_ => b.props.canOperate && i >= 0 && !r.loaded)
        .map { u =>
          val load =
            ObserveCircuit.dispatchCB(
              LoadSequence(Observer(u.displayName), r.instrument, r.obsId)
            )
          val spin = b.modState(_.copy(rowLoading = i.some))
          spin *> load
        }
        .getOrEmpty
    }
  }

  def table(b: Backend)(size: Size): VdomNode =
    if (size.width.toInt > 0) {
      Table(
        Table.props(
          disableHeader = false,
          noRowsRenderer = () =>
            <.div(
              ^.cls    := "ui center aligned segment noRows",
              ObserveStyles.noRowsSegment,
              ^.height := 230.px,
              "Session queue empty"
            ),
          overscanRowCount = ObserveStyles.overscanRowCount,
          height = 230,
          rowCount = b.props.rowCount,
          rowHeight = ObserveStyles.rowHeight,
          rowClassName = rowClassName(b.props) _,
          width = max(1, size.width.toInt),
          rowGetter = b.props.rowGetter _,
          headerClassName = ObserveStyles.tableHeader.htmlClass,
          scrollTop = b.state.tableState.scrollPosition,
          onScroll = (_, _, pos) => updateScrollPosition(b, pos),
          onRowDoubleClick = doubleClick(b),
          onRowClick = singleClick(b),
          headerHeight = ObserveStyles.headerHeight,
          rowRenderer = draggableRowRenderer(b)
        ),
        b.state.tableState.columnBuilder(size, colBuilder(b, size), b.props.columnWidths): _*
      ).vdomElement
    } else {
      <.div()
    }

  def dragStart(b: Backend, obsId: Observation.Id)(e: ReactDragEvent): Callback =
    Callback {
      e.dataTransfer.setData("text/plain", obsId.toString)
    }.when(b.props.canOperate) *> Callback.empty

  private def draggableRowRenderer(b: Backend) =
    (
      className:        String,
      columns:          Array[VdomNode],
      index:            Int,
      _:                Boolean,
      key:              String,
      rowData:          SessionQueueRow,
      onRowClick:       Option[OnRowClick],
      onRowDoubleClick: Option[OnRowClick],
      _:                Option[OnRowClick],
      _:                Option[OnRowClick],
      _:                Option[OnRowClick],
      style:            Style
    ) =>
      <.div(
        ^.cls       := className,
        ^.draggable := b.props.canOperate,
        ^.key       := key,
        ^.role      := "row",
        ^.onDragStart ==> dragStart(b, rowData.obsId.id),
        ^.style     := style.toJsObject,
        ^.onClick -->? onRowClick.map(h => h(index)),
        ^.onDoubleClick -->? onRowDoubleClick.map(h => h(index)),
        columns.toTagMod
      ): VdomElement

  private def initialState(p: Props): State =
    (
      State.tableState.replace(p.sequences.tableState) >>>
        State.prevObsIds.replace(p.obsIds) >>>
        State.prevLoggedIn.replace(p.loggedIn)
    )(State.InitialState)

  private def onResize(b: Backend): Size => Callback =
    s =>
      b.setStateL(State.lastSize)(s.some) *>
        b.modStateL(State.tableState)(
          _.recalculateWidths(s, b.props.visibleColumns, b.props.columnWidths)
        )

  private val component = ScalaComponent
    .builder[Props]
    .initialStateFromProps(initialState)
    .render(b =>
      AutoSizer(
        AutoSizer
          .props(table(b), disableHeight = true, onResize = onResize(b))
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .getDerivedStateFromProps { (props, state) =>
      Function.chain(
        ((s: State) => s.resetLoading(props)) ::
          List(
            (s: State) =>
              s.lastSize.fold(s)(ls =>
                State.userModified
                  .modify { um =>
                    // If login state changes discard user modifications
                    if (props.loggedIn =!= state.prevLoggedIn) {
                      NotModified
                    } else um
                  }
                  .andThen(
                    State.tableState.modify(
                      _.recalculateWidths(ls, props.visibleColumns, props.columnWidths)
                    )
                  )(s)
              ),
            State.prevObsIds.replace(props.obsIds),
            State.prevLoggedIn.replace(props.loggedIn)
          ).some
            .filter(_ => props.obsIds =!= state.prevObsIds || props.loggedIn =!= state.prevLoggedIn)
            .orEmpty
      )(state)
    }
    .build

}
