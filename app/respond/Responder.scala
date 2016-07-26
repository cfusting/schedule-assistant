package respond

import models.daos.BotuserDAO
import enums.ActionStates
import google.CalendarTools
import models.{Botuser, GoogleToFacebookPage, Tokens}
import play.api.Logger
import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSResponse}
import utilities.JsonUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Responder {

  val conf: Configuration
  val ws: WSClient
  val userDAO: BotuserDAO
  val log: Logger
  val facebookPageToken: String
  val calendarTools: CalendarTools

  def bigFail(implicit userId: String) = {
    log.error(s"Big Fail. Sending user $userId back to menu")
    userDAO.insertOrUpdate(Botuser(userId, ActionStates.menu.toString))
    sendJson(JsonUtil.getTextMessageJson("Dang! Something has gone wrong. Let's start over."))
    sendJson(JsonUtil.getMenuJson(userId))
  }

  def sendJson(json: JsValue): Future[WSResponse] = {
    ws.url(getConf("message.url"))
      .withQueryString("access_token" -> facebookPageToken)
      .post(json)
  }

  def storeUserName(user: Botuser)(implicit userId: String) = {
    ws.url(getConf("profile.url") + userId)
      .withQueryString("access_token" -> facebookPageToken)
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
