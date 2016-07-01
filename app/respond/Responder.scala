package respond

import dao.UserDAO
import enums.ActionStates
import models.User
import play.api.Logger
import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSResponse}
import utilities.JsonUtil

import scala.concurrent.Future

trait Responder {

  val conf: Configuration
  val ws: WSClient
  val userDAO: UserDAO
  val log: Logger

  def bigFail(implicit userId: String) = {
    log.error(s"Big Fail. Sending user $userId back to menu")
    userDAO.insertOrUpdate(User(userId, ActionStates.menu.toString))
    sendJson(JsonUtil.getTextMessageJson("Dang! Something has gone wrong. Let's start over."))
    sendJson(JsonUtil.getMenuJson(userId))
  }

  def sendJson(json: JsValue): Future[WSResponse] = {
    ws.url("https://graph.facebook.com/v2.6/me/messages")
      .withQueryString("access_token" -> conf.underlying.getString("thepenguin.token"))
      .post(json)
  }

  def getConf(prop: String) = conf.underlying.getString(prop)

  def returnToMenu(implicit userId: String) = {
    sendJson(JsonUtil.getMenuJson)
    userDAO.insertOrUpdate(User(userId, ActionStates.menu.toString))
  }
}
