package respond

import models.dao.UserDAO
import enums.ActionStates
import models.User
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
  val userDAO: UserDAO
  val log: Logger

  def bigFail(implicit userId: String) = {
    log.error(s"Big Fail. Sending user $userId back to menu")
    userDAO.insertOrUpdate(User(userId, ActionStates.menu.toString))
    sendJson(JsonUtil.getTextMessageJson("Dang! Something has gone wrong. Let's start over."))
    sendJson(JsonUtil.getMenuJson(userId))
  }

  def sendJson(json: JsValue): Future[WSResponse] = {
    ws.url(getConf("message.url"))
      .withQueryString("access_token" -> getConf("thepenguin.token"))
      .post(json)
  }

  def storeUserName(user: User)(implicit userId: String) = {
    ws.url(getConf("profile.url") + userId)
      .withQueryString("access_token" -> getConf("thepenguin.token"))
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
    userDAO.insertOrUpdate(User(userId, ActionStates.menu.toString))
  }
}
