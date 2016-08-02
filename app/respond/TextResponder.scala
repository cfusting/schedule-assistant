package respond

import javax.inject.Inject

import models.daos.BotuserDAO
import google.CalendarTools
import models.{GoogleToFacebookPage, UserAction}
import nlp.DateTimeParser
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.{Configuration, Logger}
import play.api.libs.ws.WSClient
import utilities.JsonUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class TextResponder @Inject()(override val conf: Configuration, override val ws: WSClient,
                              override val userDAO: BotuserDAO, override val calendarTools: CalendarTools,
                              override val gtfp: GoogleToFacebookPage, masterTime: DateTimeParser,
                              override val messagesApi: MessagesApi)(implicit val lang: Lang)
  extends Responder {

  override val log = Logger(this.getClass)

  def respond(text: String)(implicit userId: String): Unit = {
    log.debug("Text message received from: " + userId)
    text match {
      case "menu" | "help" =>
        sendJson(JsonUtil.getMenuJson(Messages("greeting", gtfp.name, Messages("brand")), Messages("schedule"),
          Messages("cancel"), Messages("view")))
        resetToMenuStatus
      case other =>
        userDAO.getUser(userId) onComplete {
          case Success(suc) =>
            suc match {
              case Some(user) =>
                val ar = new ActionResponder(userDAO, ws, conf, masterTime, calendarTools, gtfp, messagesApi)
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
