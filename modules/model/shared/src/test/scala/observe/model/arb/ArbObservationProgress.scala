// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import observe.model.Observation
import lucuma.core.util.arb.ArbEnumerated._
import lucuma.core.util.arb.ArbGid._
import lucuma.core.util.arb.ArbUid._
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Cogen
import org.scalacheck.Gen
import observe.model.arb.ArbTime._
import observe.model.arb.ArbNSSubexposure._
import observe.model._
import observe.model.ObserveStage.observeStageEnum
import squants.time._

trait ArbObservationProgress {
  import ArbObservationIdName._

  implicit val arbObservationProgress: Arbitrary[ObservationProgress] =
    Arbitrary {
      for {
        o <- arbitrary[Observation.IdName]
        s <- arbitrary[StepId]
        t <- arbitrary[Time]
        r <- arbitrary[Time]
        v <- arbitrary[ObserveStage]
      } yield ObservationProgress(o, s, t, r, v)
    }

  implicit val observationInProgressCogen: Cogen[ObservationProgress] =
    Cogen[(Observation.IdName, StepId, Time, Time, ObserveStage)]
      .contramap(x => (x.obsIdName, x.stepId, x.total, x.remaining, x.stage))

  implicit val arbNSObservationProgress: Arbitrary[NSObservationProgress] =
    Arbitrary {
      for {
        o <- arbitrary[Observation.IdName]
        s <- arbitrary[StepId]
        t <- arbitrary[Time]
        r <- arbitrary[Time]
        v <- arbitrary[ObserveStage]
        u <- arbitrary[NSSubexposure]
      } yield NSObservationProgress(o, s, t, r, v, u)
    }

  implicit val nsObservationInProgressCogen: Cogen[NSObservationProgress] =
    Cogen[(Observation.IdName, StepId, Time, Time, ObserveStage, NSSubexposure)]
      .contramap(x => (x.obsIdName, x.stepId, x.total, x.remaining, x.stage, x.sub))

  implicit val arbProgress: Arbitrary[Progress] =
    Arbitrary {
      for {
        o <- arbitrary[ObservationProgress]
        n <- arbitrary[NSObservationProgress]
        p <- Gen.oneOf(o, n)
      } yield p
    }

  implicit val progressCogen: Cogen[Progress] =
    Cogen[Either[ObservationProgress, NSObservationProgress]]
      .contramap {
        case x: ObservationProgress   => Left(x)
        case x: NSObservationProgress => Right(x)
      }

}

object ArbObservationProgress extends ArbObservationProgress
