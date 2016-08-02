package respond

import models.daos.BotuserDAO
import enums.ActionStates
import google.CalendarTools
import models.{Botuser, GoogleToFacebookPage}
import play.api.Logger
import play.api.Configuration
import play.api.i18n.{I18nSupport, Lang, MessagesApi, Messages}
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSResponse}
import utilities.JsonUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Responder extends I18nSupport {

  val conf: Configuration
  val ws: WSClient
  val userDAO: BotuserDAO
  val log: Logger
  val gtfp: GoogleToFacebookPage
  val calendarTools: CalendarTools
  val messagesApi: MessagesApi
  implicit val lang: Lang

  def bigFail(implicit userId: String) = {
    log.error(s"Big Fail. Sending user $userId back to menu")
    userDAO.insertOrUpdate(Botuser(userId, ActionStates.menu.toString))
    sendJson(JsonUtil.getTextMessageJson("Dang! Something has gone wrong. Let's start over."))
    sendJson(JsonUtil.getMenuJson(Messages("greeting", gtfp.name, Messages("brand")), Messages("schedule"),
      Messages("cancel"), Messages("view")))
  }

  def sendJson(json: JsValue): Future[WSResponse] = {
    ws.url(getConf("message.url"))
      .withQueryString("access_token" -> gtfp.accessToken)
      .post(json)
  }

  def storeUserName(user: Botuser)(implicit userId: String) = {
    ws.url(getConf("profile.url") + userId)
      .withQueryString("access_token" -> gtfp.accessToken)
      .get
      .onSuccess {
        case result =>
          val firstName = (result.json \ "first_name").get.as[String]
          val lastName = (result.json \ "last_name").get.as[String]
          userDAO.updateName(firstName, lastName)
      }
  }

  def getConf(prop: String) = conf.underlying.getString(prop)

  def resetToMenuStatus(implicit userId: String) = {
    userDAO.insertOrUpdate(Botuser(userId, ActionStates.menu.toString))
  }
}
