// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import scala.collection.immutable.SortedMap

import cats._
import cats.syntax.all._
import lucuma.core.util.Enumerated
import monocle.Lens
import monocle.Optional
import monocle.Prism
import monocle.macros.GenPrism
import monocle.macros.Lenses
import monocle.std.either._
import observe.model.Observation
import observe.model.Observer
import observe.model.RunningStep
import observe.model.SequenceState
import observe.model.SequenceView
import observe.model.StepId
import observe.model.SystemOverrides
import observe.model.enum._
import observe.web.client.model.ModelOps._
import shapeless.tag.@@

final case class AvailableTab(
  idName:             Observation.IdName,
  status:             SequenceState,
  instrument:         Instrument,
  runningStep:        Option[RunningStep],
  nextStepToRun:      Option[StepId],
  isPreview:          Boolean,
  active:             TabSelected,
  loading:            Boolean,
  systemOverrides:    SystemOverrides,
  overrideControls:   SectionVisibilityState,
  resourceOperations: SortedMap[Resource, ResourceRunOperation]
)

object AvailableTab {
  implicit val eq: Eq[AvailableTab] =
    Eq.by(x =>
      (x.idName,
       x.status,
       x.instrument,
       x.runningStep,
       x.nextStepToRun,
       x.isPreview,
       x.active,
       x.loading,
       x.overrideControls,
       x.systemOverrides,
       x.resourceOperations
      )
    )
}

final case class CalibrationQueueTabActive(calibrationTab: CalibrationQueueTab, active: TabSelected)

object CalibrationQueueTabActive {
  implicit val eq: Eq[CalibrationQueueTabActive] =
    Eq.by(x => (x.calibrationTab, x.active))
}

sealed trait TabSelected extends Product with Serializable
object TabSelected {
  case object Selected   extends TabSelected
  case object Background extends TabSelected

  def fromBoolean(b: Boolean): TabSelected = if (b) Selected else Background

  /** @group Typeclass Instances */
  implicit val TabSelectedEnumerated: Enumerated[TabSelected] =
    Enumerated.of(Selected, Background)

}

final case class ObserveTabActive(tab: SequenceTab, active: TabSelected)

object ObserveTabActive {
  implicit val eq: Eq[ObserveTabActive] =
    Eq.by(x => (x.tab, x.active))

}

sealed trait ObserveTab {
  def isPreview: Boolean
}

object ObserveTab {
  implicit val eq: Eq[ObserveTab] =
    Eq.instance {
      case (a: SequenceTab, b: SequenceTab)                 => a === b
      case (a: CalibrationQueueTab, b: CalibrationQueueTab) => a === b
      case _                                                => false
    }

  val previewTab: Prism[ObserveTab, PreviewSequenceTab]       =
    GenPrism[ObserveTab, PreviewSequenceTab]
  val instrumentTab: Prism[ObserveTab, InstrumentSequenceTab] =
    GenPrism[ObserveTab, InstrumentSequenceTab]
  val calibrationTab: Prism[ObserveTab, CalibrationQueueTab]  =
    GenPrism[ObserveTab, CalibrationQueueTab]
  val sequenceTab: Prism[ObserveTab, SequenceTab]             =
    Prism.partial[ObserveTab, SequenceTab] {
      case p: PreviewSequenceTab    => p
      case i: InstrumentSequenceTab => i
    }(identity)

}

@Lenses
final case class CalibrationQueueTab(state: BatchExecState, observer: Option[Observer])
    extends ObserveTab {
  val isPreview: Boolean = false
}

object CalibrationQueueTab {
  val Empty: CalibrationQueueTab =
    CalibrationQueueTab(BatchExecState.Idle, None)

  implicit val eq: Eq[CalibrationQueueTab] =
    Eq.by(x => (x.state, x.observer))
}

sealed trait SequenceTab extends ObserveTab {
  val tabOperations: TabOperations

  def subsystemControlVisible: SectionVisibilityState =
    this match {
      case i: InstrumentSequenceTab => i.subsysControls
      case _: PreviewSequenceTab    => SectionVisibilityState.SectionClosed
    }

  def instrument: Instrument =
    this match {
      case i: InstrumentSequenceTab => i.inst
      case i: PreviewSequenceTab    => i.currentSequence.metadata.instrument
    }

  def sequence: SequenceView =
    this match {
      // Returns the current sequence or if empty the last completed one
      case i: InstrumentSequenceTab => i.seq
      case i: PreviewSequenceTab    => i.currentSequence
    }

  def obsIdName: Observation.IdName = sequence.idName

  def stepConfigDisplayed: Option[StepId] =
    this match {
      case i: InstrumentSequenceTab => i.stepConfig
      case i: PreviewSequenceTab    => i.stepConfig
    }

  def isPreview: Boolean =
    this match {
      case _: InstrumentSequenceTab => false
      case _                        => true
    }

  def isComplete: Boolean =
    this match {
      case InstrumentSequenceTab(_,
                                 Left(_: InstrumentSequenceTab.CompletedSequenceView),
                                 _,
                                 _,
                                 _,
                                 _,
                                 _
          ) =>
        true
      case _ => false
    }

  def runningStep: Option[RunningStep] =
    this match {
      case _: InstrumentSequenceTab => sequence.runningStep
      case _                        => none
    }

  def nextStepToRun: Option[StepId] = sequence.nextStepToRun

  def loading: Boolean =
    this match {
      case _: InstrumentSequenceTab => false
      case p: PreviewSequenceTab    => p.isLoading
    }

  def selectedStep: Option[StepId] =
    this match {
      case i: InstrumentSequenceTab => i.selected
      case _                        => none
    }
}

object SequenceTab {
  implicit val eq: Eq[SequenceTab] =
    Eq.instance {
      case (a: InstrumentSequenceTab, b: InstrumentSequenceTab) => a === b
      case (a: PreviewSequenceTab, b: PreviewSequenceTab)       => a === b
      case _                                                    => false
    }

  val stepConfigL: Lens[SequenceTab, Option[StepId]] =
    Lens[SequenceTab, Option[StepId]] {
      case t: InstrumentSequenceTab => t.stepConfig
      case t: PreviewSequenceTab    => t.stepConfig
    }(n => {
      case t: InstrumentSequenceTab => t.copy(stepConfig = n)
      case t: PreviewSequenceTab    => t.copy(stepConfig = n)
    })

  val tabOperationsL: Lens[SequenceTab, TabOperations] =
    Lens[SequenceTab, TabOperations] {
      case t: InstrumentSequenceTab => t.tabOperations
      case t: PreviewSequenceTab    => t.tabOperations
    }(n => {
      case t: InstrumentSequenceTab => t.copy(tabOperations = n)
      case t: PreviewSequenceTab    => t.copy(tabOperations = n)
    })

  val resourcesRunOperationsL: Lens[SequenceTab, SortedMap[Resource, ResourceRunOperation]] =
    SequenceTab.tabOperationsL.andThen(TabOperations.resourceRunRequested)
}

@Lenses
final case class InstrumentSequenceTab(
  inst:            Instrument,
  curSequence:     Either[
    InstrumentSequenceTab.CompletedSequenceView,
    InstrumentSequenceTab.LoadedSequenceView
  ],
  stepConfig:      Option[StepId],
  selected:        Option[StepId],
  tabOperations:   TabOperations,
  systemOverrides: SystemOverrides,
  subsysControls:  SectionVisibilityState
) extends SequenceTab {
  val seq: SequenceView = curSequence match {
    case Right(x) => x
    case Left(x)  => x
  }
}

object InstrumentSequenceTab {
  // Marker traits
  trait LoadedSV
  trait CompletedSV

  type LoadedSequenceView    = SequenceView @@ LoadedSV
  type CompletedSequenceView = SequenceView @@ CompletedSV

  private implicit val loadedEq: Eq[LoadedSequenceView]       = Eq.by(identity)
  private implicit val completedEq: Eq[CompletedSequenceView] = Eq.by(identity)

  implicit val eq: Eq[InstrumentSequenceTab] =
    Eq.by(x =>
      (x.instrument,
       x.sequence,
       x.stepConfig,
       x.selected,
       x.tabOperations,
       x.systemOverrides,
       x.subsysControls
      )
    )

  implicit val completedSequence: Optional[InstrumentSequenceTab, CompletedSequenceView] =
    InstrumentSequenceTab.curSequence.andThen(
      stdLeft[InstrumentSequenceTab.CompletedSequenceView, InstrumentSequenceTab.LoadedSequenceView]
    )

  implicit val loadedSequence: Optional[InstrumentSequenceTab, LoadedSequenceView] =
    InstrumentSequenceTab.curSequence.andThen(
      stdRight[InstrumentSequenceTab.CompletedSequenceView,
               InstrumentSequenceTab.LoadedSequenceView
      ]
    )
}

@Lenses
final case class PreviewSequenceTab(
  currentSequence: SequenceView,
  stepConfig:      Option[StepId],
  isLoading:       Boolean,
  tabOperations:   TabOperations
) extends SequenceTab

object PreviewSequenceTab {
  implicit val eq: Eq[PreviewSequenceTab] =
    Eq.by(x => (x.currentSequence, x.stepConfig, x.isLoading, x.tabOperations))
}
