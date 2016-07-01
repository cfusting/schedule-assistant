package respond

import javax.inject.Inject

import dao.UserDAO
import enums.ActionStates
import models.UserAction
import play.api.{Configuration, Logger}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class TextResponder @Inject()(override val conf: Configuration, override val ws: WSClient,
                              override val userDAO: UserDAO, actionResponder: ActionResponder) extends Responder {

  override val log = Logger(this.getClass)

  def respond(text: String)(implicit userId: String): Unit = {
    log.debug("Text message received from: " + userId)
    text match {
      case "menu" => returnToMenu
      case other =>
        userDAO.getUser(userId) onComplete {
          case Success(suc) =>
            suc match {
              case Some(user) => actionResponder.respond(UserAction(user, text))
              case None => returnToMenu
            }
          case Failure(ex) =>
            log.error("DB failure for user with id: " + userId + ". Message: " + ex.getMessage)
            bigFail
        }
    }

  }

}
