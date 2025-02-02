// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Id
import cats.effect.IO
import cats.implicits._
import cats.data.NonEmptyList
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.scalatest.Inside.inside
import org.scalatest.NonImplicitAssertions
import org.scalatest.matchers.should.Matchers
import observe.engine._
import observe.model._
import observe.model.enum._
import observe.model.enum.Resource.TCS
import observe.server.TestCommon._
import monocle.function.Index.mapIndex
import observe.common.test.stepId

class StepsViewSpec extends TestCommon with Matchers with NonImplicitAssertions {

  "StepsView configStatus" should
    "build empty without tasks" in {
      StepsView.configStatus(Nil) shouldBe List.empty
    }
  it should "be all running if none has a result" in {
    val status                                = List(Resource.TCS -> ActionStatus.Running)
    val executions: List[ParallelActions[Id]] = List(NonEmptyList.one(running(Resource.TCS)))
    StepsView.configStatus(executions) shouldBe status
  }
  it should "be all running if none has a result 2" in {
    val status                                =
      List(Resource.TCS -> ActionStatus.Running, Instrument.GmosN -> ActionStatus.Running)
    val executions: List[ParallelActions[Id]] =
      List(NonEmptyList.of(running(Resource.TCS), running(Instrument.GmosN)))
    StepsView.configStatus(executions) shouldBe status
  }
  it should "be some complete and some running if none has a result even when the previous execution is complete" in {
    val status                                =
      List(Resource.TCS -> ActionStatus.Completed, Instrument.GmosN -> ActionStatus.Running)
    val executions: List[ParallelActions[Id]] =
      List(NonEmptyList.one(done(Resource.TCS)),
           NonEmptyList.of(done(Resource.TCS), running(Instrument.GmosN))
      )
    StepsView.configStatus(executions) shouldBe status
  }
  it should "be some complete and some pending if one will be done in the future" in {
    val status                                =
      List(Resource.TCS -> ActionStatus.Completed, Instrument.GmosN -> ActionStatus.Running)
    val executions: List[ParallelActions[Id]] = List(
      NonEmptyList.one(running(Instrument.GmosN)),
      NonEmptyList.of(done(Resource.TCS), done(Instrument.GmosN))
    )
    StepsView.configStatus(executions) shouldBe status
  }
  it should "stop at the first with running steps" in {
    val executions: List[ParallelActions[Id]] = List(
      NonEmptyList.one(running(Instrument.GmosN)),
      NonEmptyList.of(running(Instrument.GmosN), running(Resource.TCS))
    )
    val status                                =
      List(Resource.TCS -> ActionStatus.Pending, Instrument.GmosN -> ActionStatus.Running)
    StepsView.configStatus(executions) shouldBe status
  }
  it should "stop evaluating where at least one is running even while some are done" in {
    val executions: List[ParallelActions[Id]] = List(
      NonEmptyList.of(done(Resource.TCS), done(Instrument.GmosN)),
      NonEmptyList.of(done(Resource.TCS), running(Instrument.GmosN)),
      NonEmptyList.of(pendingAction(Resource.TCS),
                      pendingAction(Instrument.GmosN),
                      pendingAction(Resource.Gcal)
      )
    )
    val status                                = List(Resource.TCS -> ActionStatus.Completed,
                      Resource.Gcal    -> ActionStatus.Pending,
                      Instrument.GmosN -> ActionStatus.Running
    )
    StepsView.configStatus(executions) shouldBe status
  }

  "StepsView pending configStatus" should
    "build empty without tasks" in {
      StepsView.configStatus(Nil) shouldBe List.empty
    }
  it should "be all pending while one is running" in {
    val status                                = List(Resource.TCS -> ActionStatus.Pending)
    val executions: List[ParallelActions[Id]] = List(NonEmptyList.one(pendingAction(Resource.TCS)))
    StepsView.pendingConfigStatus(executions) shouldBe status
  }
  it should "be all pending with mixed" in {
    val status                                =
      List(Resource.TCS -> ActionStatus.Pending, Instrument.GmosN -> ActionStatus.Pending)
    val executions: List[ParallelActions[Id]] =
      List(NonEmptyList.of(pendingAction(Resource.TCS), done(Instrument.GmosN)))
    StepsView.pendingConfigStatus(executions) shouldBe status
  }
  it should "be all pending on mixed combinations" in {
    val status                                =
      List(Resource.TCS -> ActionStatus.Pending, Instrument.GmosN -> ActionStatus.Pending)
    val executions: List[ParallelActions[Id]] =
      List(NonEmptyList.one(done(Resource.TCS)),
           NonEmptyList.of(done(Resource.TCS), pendingAction(Instrument.GmosN))
      )
    StepsView.pendingConfigStatus(executions) shouldBe status
  }
  it should "be all pending with multiple resources" in {
    val executions: List[ParallelActions[Id]] = List(
      NonEmptyList.of(done(Resource.TCS), pendingAction(Instrument.GmosN)),
      NonEmptyList.of(done(Resource.TCS), pendingAction(Instrument.GmosN)),
      NonEmptyList.of(done(Resource.TCS),
                      pendingAction(Instrument.GmosN),
                      pendingAction(Resource.Gcal)
      )
    )
    val status                                = List(Resource.TCS -> ActionStatus.Pending,
                      Resource.Gcal    -> ActionStatus.Pending,
                      Instrument.GmosN -> ActionStatus.Pending
    )
    StepsView.pendingConfigStatus(executions) shouldBe status
  }

  "StepsView observeStatus" should
    "be pending on empty" in {
      StepsView.observeStatus(Nil) shouldBe ActionStatus.Pending
    }
  it should "be running if there is an action observe" in {
    val executions: List[ParallelActions[Id]] = List(NonEmptyList.of(done(Resource.TCS), observing))
    StepsView.observeStatus(executions) shouldBe ActionStatus.Running
  }
  it should "be done if there is a result observe" in {
    val executions: List[ParallelActions[Id]] = List(NonEmptyList.of(done(Resource.TCS), observed))
    StepsView.observeStatus(executions) shouldBe ActionStatus.Completed
  }
  it should "be running if there is a partial result with the file id" in {
    val executions: List[ParallelActions[Id]] =
      List(NonEmptyList.of(done(Resource.TCS), fileIdReady))
    StepsView.observeStatus(executions) shouldBe ActionStatus.Running
  }
  it should "be paused if there is a paused observe" in {
    val executions: List[ParallelActions[Id]] = List(NonEmptyList.of(done(Resource.TCS), paused))
    StepsView.observeStatus(executions) shouldBe ActionStatus.Paused
  }

  "StepsView setOperator" should "set operator's name" in {
    val operator = Operator("Joe")
    val s0       = EngineState.default[IO]
    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      sf <- advanceN(q, s0, observeEngine.setOperator(q, UserDetails("", ""), operator), 2)
    } yield inside(sf.flatMap(EngineState.operator.get)) { case Some(op) =>
      op shouldBe operator
    }).unsafeRunSync()
  }

  "StepsView setImageQuality" should "set Image Quality condition" in {
    val iq = ImageQuality.Percent20
    val s0 = EngineState.default[IO]

    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      sf <- advanceN(q, s0, observeEngine.setImageQuality(q, iq, UserDetails("", "")), 2)
    } yield inside(sf.map(EngineState.conditions.andThen(Conditions.iq).get)) { case Some(op) =>
      op shouldBe iq
    }).unsafeRunSync()

  }

  "StepsView setWaterVapor" should "set Water Vapor condition" in {
    val wv = WaterVapor.Percent80
    val s0 = EngineState.default[IO]
    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      sf <- advanceN(q, s0, observeEngine.setWaterVapor(q, wv, UserDetails("", "")), 2)
    } yield inside(sf.map(EngineState.conditions.andThen(Conditions.wv).get(_))) { case Some(op) =>
      op shouldBe wv
    }).unsafeRunSync()
  }

  "StepsView setCloudCover" should "set Cloud Cover condition" in {
    val cc = CloudCover.Percent70
    val s0 = EngineState.default[IO]
    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      sf <- advanceN(q, s0, observeEngine.setCloudCover(q, cc, UserDetails("", "")), 2)
    } yield inside(sf.map(EngineState.conditions.andThen(Conditions.cc).get(_))) { case Some(op) =>
      op shouldBe cc
    }).unsafeRunSync()
  }

  "StepsView setSkyBackground" should "set Sky Background condition" in {
    val sb = SkyBackground.Percent50
    val s0 = EngineState.default[IO]
    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      sf <- advanceN(q, s0, observeEngine.setSkyBackground(q, sb, UserDetails("", "")), 2)
    } yield inside(sf.map(EngineState.conditions.andThen(Conditions.sb).get(_))) { case Some(op) =>
      op shouldBe sb
    }).unsafeRunSync()
  }

  "StepsView setObserver" should "set observer's name" in {
    val observer = Observer("Joe")
    val s0       = ODBSequencesLoader
      .loadSequenceEndo[IO](seqObsId1, sequence(seqObsId1), executeEngine)
      .apply(EngineState.default[IO])
    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      sf <-
        advanceN(q, s0, observeEngine.setObserver(q, seqObsId1, UserDetails("", ""), observer), 2)
    } yield inside(
      sf.flatMap(
        EngineState
          .sequences[IO]
          .andThen(mapIndex[Observation.Id, SequenceData[IO]].index(seqObsId1))
          .getOption
      ).flatMap(_.observer)
    ) { case Some(op) =>
      op shouldBe observer
    }).unsafeRunSync()
  }

  "StepsView" should "not run 2nd sequence because it's using the same resource" in {
    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      seqObsId1,
      sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.F2, TCS)),
      executeEngine
    ) >>>
      ODBSequencesLoader.loadSequenceEndo[IO](
        seqObsId2,
        sequenceWithResources(seqObsId2, Instrument.F2, Set(Instrument.F2)),
        executeEngine
      ) >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.init))(EngineState.default[IO])

    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      sf <- advanceOne(
              q,
              s0,
              observeEngine.start(q,
                                  seqObsId2,
                                  UserDetails("", ""),
                                  Observer(""),
                                  clientId,
                                  RunOverride.Default
              )
            )
    } yield inside(sf.flatMap(EngineState.sequenceStateIndex(seqObsId2).getOption).map(_.status)) {
      case Some(status) => assert(status.isIdle)
    }).unsafeRunSync()

  }

  it should "run 2nd sequence when there are no shared resources" in {
    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      seqObsId1,
      sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.F2, TCS)),
      executeEngine
    ) >>>
      ODBSequencesLoader.loadSequenceEndo[IO](
        seqObsId2,
        sequenceWithResources(seqObsId2, Instrument.GmosS, Set(Instrument.GmosS)),
        executeEngine
      ) >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.init))(EngineState.default[IO])

    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      sf <- advanceN(
              q,
              s0,
              observeEngine.start(q,
                                  seqObsId2,
                                  UserDetails("", ""),
                                  Observer(""),
                                  clientId,
                                  RunOverride.Default
              ),
              2
            )
    } yield inside(sf.flatMap(EngineState.sequenceStateIndex(seqObsId2).getOption).map(_.status)) {
      case Some(status) => assert(status.isRunning)
    }).unsafeRunSync()
  }

  "StepsView configSystem" should "run a system configuration" in {
    val s0 = ODBSequencesLoader
      .loadSequenceEndo[IO](
        seqObsId1,
        sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.F2, TCS)),
        executeEngine
      )
      .apply(EngineState.default[IO])

    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      sf <-
        advanceN(q,
                 s0,
                 observeEngine.configSystem(q,
                                            seqObsId1,
                                            Observer(""),
                                            UserDetails("", ""),
                                            stepId(1),
                                            TCS,
                                            clientId
                 ),
                 3
        )
    } yield inside(
      sf.flatMap(
        EngineState
          .sequences[IO]
          .andThen(mapIndex[Observation.Id, SequenceData[IO]].index(seqObsId1))
          .getOption
      )
    ) { case Some(s) =>
      assertResult(Some(Action.ActionState.Started))(
        s.seqGen.configActionCoord(stepId(1), TCS).map(s.seq.getSingleState)
      )
    }).unsafeRunSync()
  }

  it should "not run a system configuration if sequence is running" in {
    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      seqObsId1,
      sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.F2, TCS)),
      executeEngine
    ) >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.init))(EngineState.default[IO])

    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      sf <-
        advanceOne(q,
                   s0,
                   observeEngine.configSystem(q,
                                              seqObsId1,
                                              Observer(""),
                                              UserDetails("", ""),
                                              stepId(1),
                                              TCS,
                                              clientId
                   )
        )
    } yield inside(
      sf.flatMap(
        EngineState
          .sequences[IO]
          .andThen(mapIndex[Observation.Id, SequenceData[IO]].index(seqObsId1))
          .getOption
      )
    ) { case Some(s) =>
      assertResult(Some(Action.ActionState.Idle))(
        s.seqGen.configActionCoord(stepId(1), TCS).map(s.seq.getSingleState)
      )
    }).unsafeRunSync()
  }

  it should "not run a system configuration if system is in use" in {
    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      seqObsId1,
      sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.F2, TCS)),
      executeEngine
    ) >>>
      ODBSequencesLoader.loadSequenceEndo[IO](
        seqObsId2,
        sequenceWithResources(seqObsId2, Instrument.F2, Set(Instrument.F2)),
        executeEngine
      ) >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.init))(EngineState.default[IO])

    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      sf <-
        advanceOne(
          q,
          s0,
          observeEngine.configSystem(q,
                                     seqObsId2,
                                     Observer(""),
                                     UserDetails("", ""),
                                     stepId(1),
                                     Instrument.F2,
                                     clientId
          )
        )
    } yield inside(
      sf.flatMap(
        EngineState
          .sequences[IO]
          .andThen(mapIndex[Observation.Id, SequenceData[IO]].index(seqObsId2))
          .getOption
      )
    ) { case Some(s) =>
      assertResult(Some(Action.ActionState.Idle))(
        s.seqGen.configActionCoord(stepId(1), Instrument.F2).map(s.seq.getSingleState)
      )
    }).unsafeRunSync()
  }

  it should "run a system configuration when other sequence is running with other systems" in {
    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      seqObsId1,
      sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.GmosS, TCS)),
      executeEngine
    ) >>>
      ODBSequencesLoader.loadSequenceEndo[IO](
        seqObsId2,
        sequenceWithResources(seqObsId2, Instrument.F2, Set(Instrument.F2)),
        executeEngine
      ) >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.init))(EngineState.default[IO])

    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      sf <-
        advanceN(
          q,
          s0,
          observeEngine
            .configSystem(q,
                          seqObsId2,
                          Observer(""),
                          UserDetails("", ""),
                          stepId(1),
                          Instrument.F2,
                          clientId
            ),
          3
        )
    } yield inside(
      sf.flatMap(
        EngineState
          .sequences[IO]
          .andThen(mapIndex[Observation.Id, SequenceData[IO]].index(seqObsId2))
          .getOption
      )
    ) { case Some(s) =>
      assertResult(Some(Action.ActionState.Started))(
        s.seqGen.configActionCoord(stepId(1), Instrument.F2).map(s.seq.getSingleState)
      )
    }).unsafeRunSync()
  }

  "StepsView startFrom" should "start a sequence from an arbitrary step" in {
    val s0        = ODBSequencesLoader
      .loadSequenceEndo[IO](seqObsId1, sequenceNSteps(seqObsId1, 5), executeEngine)
      .apply(EngineState.default[IO])
    val runStepId = stepId(3)

    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      _  <- observeEngine.startFrom(q,
                                    seqObsId1,
                                    Observer(""),
                                    runStepId,
                                    clientId,
                                    RunOverride.Default
            )
      sf <- observeEngine
              .stream(Stream.fromQueueUnterminated(q))(s0)
              .map(_._2)
              .takeThrough(_.sequences.values.exists(_.seq.status.isRunning))
              .compile
              .last
    } yield inside(
      sf.flatMap(EngineState.sequenceStateIndex(seqObsId1).getOption).map(_.toSequence.steps)
    ) { case Some(steps) =>
      assertResult(Some(StepState.Skipped))(steps.get(0).map(_.status))
      assertResult(Some(StepState.Skipped))(steps.get(1).map(_.status))
      assertResult(Some(StepState.Completed))(steps.get(2).map(_.status))
    }).unsafeRunSync()
  }

  "StepsView startFrom" should "not start the sequence if there is a resource conflict" in {
    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      seqObsId1,
      sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.F2, TCS)),
      executeEngine
    ) >>>
      ODBSequencesLoader.loadSequenceEndo[IO](
        seqObsId2,
        sequenceWithResources(seqObsId2, Instrument.F2, Set(Instrument.F2)),
        executeEngine
      ) >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.init))(EngineState.default[IO])

    val runStepId = stepId(2)

    (for {
      q  <- Queue.bounded[IO, executeEngine.EventType](10)
      _  <- observeEngine.startFrom(q,
                                    seqObsId2,
                                    Observer(""),
                                    runStepId,
                                    clientId,
                                    RunOverride.Default
            )
      sf <- observeEngine
              .stream(Stream.fromQueueUnterminated(q))(s0)
              .map(_._2)
              .takeThrough(_.sequences.get(seqObsId2).exists(_.seq.status.isRunning))
              .compile
              .last
    } yield inside(sf.flatMap(EngineState.sequenceStateIndex(seqObsId2).getOption).map(_.status)) {
      case Some(status) => assert(status.isIdle)
    }).unsafeRunSync()
  }

}
