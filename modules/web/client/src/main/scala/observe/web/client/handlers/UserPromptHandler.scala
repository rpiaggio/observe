// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import cats.syntax.all._
import diode.ActionHandler
import diode.ActionResult
import diode.Effect
import diode.ModelRW
import observe.model.UserPrompt
import observe.model.events.UserPromptNotification
import observe.web.client.actions._
import observe.web.client.model._

class UserPromptHandler[M](modelRW: ModelRW[M, UserPromptState])
    extends ActionHandler(modelRW)
    with Handlers[M, UserPromptState] {

  val lens = UserPromptState.notification

  def handleUserNotification: PartialFunction[Any, ActionResult[M]] = {
    case ServerMessage(UserPromptNotification(not, _)) =>
      // Update the model as load failed
      val modelUpdateE = not match {
        case UserPrompt.ChecksOverride(idName, _, _, _) => Effect(Future(RunStartFailed(idName.id)))
      }
      updatedLE(lens.replace(not.some), modelUpdateE)
  }

  def handleClosePrompt: PartialFunction[Any, ActionResult[M]] = { case CloseUserPromptBox(x) =>
    val overrideEffect = this.value.notification match {
      case Some(UserPrompt.ChecksOverride(id, stp, _, _)) if x === UserPromptResult.Cancel =>
        Effect(Future(RequestRunFrom(id.id, stp, RunOptions.ChecksOverride)))
      case _                                                                               => VoidEffect
    }
    updatedLE(lens.replace(none), overrideEffect)
  }

  def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleUserNotification, handleClosePrompt).combineAll
}
