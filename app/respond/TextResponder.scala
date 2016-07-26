package respond

import javax.inject.Inject

import models.daos.BotuserDAO
import google.CalendarTools
import models.UserAction
import nlp.{DateTimeParser, MasterTime}
import play.api.{Configuration, Logger}
import play.api.libs.ws.WSClient
import utilities.JsonUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class TextResponder @Inject()(override val conf: Configuration, override val ws: WSClient,
                              override val userDAO: BotuserDAO, override val calendarTools: CalendarTools,
                              override val facebookPageToken: String, masterTime: DateTimeParser)
  extends Responder {

  override val log = Logger(this.getClass)

  def respond(text: String)(implicit userId: String): Unit = {
    log.debug("Text message received from: " + userId)
    text match {
      case "menu" | "help" =>
        sendJson(JsonUtil.getTextMessageJson("Hi! You can use the menu at the bottom left of your " +
          "chat box to " + "get " + "started."))
        resetToMenuStatus
      case other =>
        userDAO.getUser(userId) onComplete {
          case Success(suc) =>
            suc match {
              case Some(user) =>
                val ar = new ActionResponder(userDAO, ws, conf, masterTime, calendarTools, facebookPageToken)
                ar.respond(UserAction(user, text))
              case None => resetToMenuStatus
            }
          case Failure(ex) =>
            log.error("DB failure for user with id: " + userId + ". Message: " + ex.getMessage)
            bigFail
        }
    }

  }

}
