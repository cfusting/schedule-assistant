package utilities

import controllers.JsonConversions._
import models._
import play.api.libs.json.JsValue
import play.api.libs.json._

object JsonUtil {

  def getMenuJson(greeting: String, schedule: String, cancel: String, view: String)(implicit sd: String): JsValue = {
    val menu = Outgoing(
      Recipient(sd),
      OutMessage(
        Some(OutAttachment(
          "template",
          Payload("button", greeting, List(
            Button("postback", None, Some(schedule), Some("schedule")),
            Button("postback", None, Some(cancel), Some("cancel")),
            Button("postback", None, Some(view), Some("view"))
          ))
        )), None)
    )
    Json.toJson(menu)
  }

  def getTextMessageJson(text: String)(implicit userid: String): JsValue = {
    val confirmation = Outgoing(
      Recipient(userid),
      OutMessage(
        None, Some(text)
      )
    )
    Json.toJson(confirmation)
  }

}
