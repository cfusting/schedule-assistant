package models.services

import javax.inject.Inject

import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.Future

class CommunicationService @Inject()(ws: WSClient, conf: Configuration) {

  def sendJson(json: JsValue, facebookPageAccessToken: String): Future[WSResponse] = {
    ws.url(conf.underlying.getString("message.url"))
      .withQueryString("access_token" -> facebookPageAccessToken)
      .post(json)
  }

}
