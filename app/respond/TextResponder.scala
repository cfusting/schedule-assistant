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
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class TextResponder @Inject()(override val conf: Configuration, override val ws: WSClient,
                              override val userDAO: BotuserDAO, override val calendarTools: CalendarTools,
                              override val gtfp: GoogleToFacebookPage, masterTime: DateTimeParser,
                              override val messagesApi: MessagesApi)(implicit val lang: Lang)
  extends Responder {

  override val log = Logger(this.getClass)
  override var prefix = ""

  def respond(text: String)(implicit userId: String): Unit = {
    prefix = s"||$userId||$text||"
    log.info(prefix)
    text match {
      case "menu" | "help" =>
        sendJson(JsonUtil.getMenuJson(Messages("greeting", gtfp.name, Messages("brand")), Messages("schedule"),
          Messages("cancel"), Messages("view")))
        resetToMenuStatus
      case other =>
        (for {
          maybeUser <- userDAO.getUser(userId)
          user <- Future(maybeUser.get)
        } yield {
          val ar = new ActionResponder(userDAO, ws, conf, masterTime, calendarTools, gtfp, messagesApi)
          ar.respond(UserAction(user, text, None))
        }).recover {
          case NonFatal(ex) =>
            log.error(s"$prefix${ex.toString}")
            bigFail
        }
    }
  }

}
