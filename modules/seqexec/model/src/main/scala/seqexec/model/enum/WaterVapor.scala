// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.model.enum

import cats.{ Eq, Show }

sealed abstract class WaterVapor(val toInt: Int, val label: String)
  extends Product with Serializable

object WaterVapor {

  case object Percent20 extends WaterVapor(20,  "20%/Low")
  case object Percent50 extends WaterVapor(50,  "50%/Median")
  case object Percent80 extends WaterVapor(80,  "85%/High")
  case object Any       extends WaterVapor(100, "Any")

  val all: List[WaterVapor] =
    List(Percent20, Percent50, Percent80, Any)

  implicit val equal: Eq[WaterVapor] =
    Eq.fromUniversalEquals

  implicit val showWaterVapor: Show[WaterVapor] =
    Show.show(_.label)

}
