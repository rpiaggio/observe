// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Cogen
import lucuma.core.util.arb.ArbEnumerated._
import lucuma.core.util.arb.ArbGid._
import lucuma.core.util.arb.ArbUid._
import observe.model._
import observe.model.enum._
import observe.model.arb.ArbStepConfig._
import observe.model.arb.ArbStepState._
import observe.model.arb.ArbDhsTypes._

trait ArbStandardStep {

  implicit val stsArb = Arbitrary[StandardStep] {
    for {
      id <- arbitrary[StepId]
      c  <- stepConfigGen
      s  <- arbitrary[StepState]
      b  <- arbitrary[Boolean]
      k  <- arbitrary[Boolean]
      f  <- arbitrary[Option[dhs.ImageFileId]]
      cs <- arbitrary[List[(Resource, ActionStatus)]]
      os <- arbitrary[ActionStatus]
    } yield new StandardStep(id = id,
                             config = c,
                             status = s,
                             breakpoint = b,
                             skip = k,
                             fileId = f,
                             configStatus = cs,
                             observeStatus = os
    )
  }

  implicit val standardStepCogen: Cogen[StandardStep] =
    Cogen[
      (
        StepId,
        Map[SystemName, Map[String, String]],
        StepState,
        Boolean,
        Boolean,
        Option[dhs.ImageFileId],
        List[(Resource, ActionStatus)],
        ActionStatus
      )
    ].contramap(s =>
      (s.id, s.config, s.status, s.breakpoint, s.skip, s.fileId, s.configStatus, s.observeStatus)
    )

}

object ArbStandardStep extends ArbStandardStep
