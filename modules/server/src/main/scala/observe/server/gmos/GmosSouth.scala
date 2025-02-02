// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.syntax.all._
import edu.gemini.spModel.gemini.gmos.GmosSouthType
import edu.gemini.spModel.gemini.gmos.GmosSouthType.FPUnitSouth._
import edu.gemini.spModel.gemini.gmos.InstGmosCommon.FPU_PROP_NAME
import edu.gemini.spModel.gemini.gmos.InstGmosCommon.STAGE_MODE_PROP
import edu.gemini.spModel.gemini.gmos.InstGmosSouth._
import org.typelevel.log4cats.Logger
import lucuma.core.enums.LightSinkName
import observe.model.enum.Instrument
import observe.server.CleanConfig
import observe.server.CleanConfig.extractItem
import observe.server.ConfigUtilOps
import observe.server.ConfigUtilOps._
import observe.server.InstrumentSpecifics
import observe.server.ObserveFailure
import observe.server.StepType
import observe.server.gmos.Gmos.SiteSpecifics
import observe.server.gmos.GmosController.SouthTypes
import observe.server.gmos.GmosController.southConfigTypes
import observe.server.keywords.DhsClient
import observe.server.tcs.FOCAL_PLANE_SCALE
import squants.Length
import squants.space.Arcseconds
import cats.effect.{ Ref, Temporal }

final case class GmosSouth[F[_]: Temporal: Logger] private (
  c:         GmosSouthController[F],
  dhsClient: DhsClient[F],
  nsCmdR:    Ref[F, Option[NSObserveCommand]]
) extends Gmos[F, SouthTypes](
      c,
      new SiteSpecifics[SouthTypes] {
        val fpuDefault: GmosSouthType.FPUnitSouth = FPU_NONE

        def extractFilter(
          config: CleanConfig
        ): Either[ConfigUtilOps.ExtractFailure, SouthTypes#Filter] =
          config.extractInstAs[SouthTypes#Filter](FILTER_PROP)

        def extractDisperser(
          config: CleanConfig
        ): Either[ConfigUtilOps.ExtractFailure, GmosSouthType.DisperserSouth] =
          config.extractInstAs[SouthTypes#Disperser](DISPERSER_PROP)

        def extractFPU(
          config: CleanConfig
        ): Either[ConfigUtilOps.ExtractFailure, GmosSouthType.FPUnitSouth] =
          config.extractInstAs[SouthTypes#FPU](FPU_PROP_NAME)

        def extractStageMode(
          config: CleanConfig
        ): Either[ConfigUtilOps.ExtractFailure, GmosSouthType.StageModeSouth] =
          config.extractInstAs[SouthTypes#GmosStageMode](STAGE_MODE_PROP)

        def isCustomFPU(config: CleanConfig): Boolean =
          (extractFPU(config), extractCustomFPU(config)) match {
            case (Right(builtIn), Right(_)) => builtIn.isCustom
            case (_, Right(_))              => true
            case _                          => false
          }
      },
      nsCmdR
    )(
      southConfigTypes
    ) {
  override val resource: Instrument      = Instrument.GmosS
  override val dhsInstrumentName: String = "GMOS-S"
}

object GmosSouth {
  val name: String = INSTRUMENT_NAME_PROP

  def apply[F[_]: Temporal: Logger](
    c:         GmosController[F, SouthTypes],
    dhsClient: DhsClient[F],
    nsCmdR:    Ref[F, Option[NSObserveCommand]]
  ): GmosSouth[F] = new GmosSouth[F](c, dhsClient, nsCmdR)

  object specifics extends InstrumentSpecifics {
    override val instrument: Instrument = Instrument.GmosS

    override def calcStepType(
      config:     CleanConfig,
      isNightSeq: Boolean
    ): Either[ObserveFailure, StepType] =
      Gmos.calcStepType(instrument, config, isNightSeq)

    override def sfName(config: CleanConfig): LightSinkName = LightSinkName.Gmos

    // TODO Use different value if using electronic offsets
    override val oiOffsetGuideThreshold: Option[Length] =
      (Arcseconds(0.01) / FOCAL_PLANE_SCALE).some

  }

}
