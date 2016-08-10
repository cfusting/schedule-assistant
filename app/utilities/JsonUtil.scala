package utilities

import controllers.JsonConversions._
import models._
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json._

object JsonUtil {

  val log = Logger(this.getClass)

  def getMenuJson(greeting: String, schedule: String, cancel: String, view: String)(implicit sd: String): JsValue = {
    val menu = Outgoing(
      Recipient(sd),
      OutMessage(
        Some(OutAttachment(
          "template",
          Payload("button", greeting, List(
            Button("postback", None, Some(schedule), Some(Json.toJson(BotPayload("schedule", None)).toString())),
            Button("postback", None, Some(cancel), Some(Json.toJson(BotPayload("cancel", None)).toString())),
            Button("postback", None, Some(view), Some(Json.toJson(BotPayload("view", None)).toString()))
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

  def getTextMessageWithNotWhatIWantedJson(text: String, notWhatIWanted: String, payload: BotPayload)
                                          (implicit userId: String): JsValue = {
    val message = Outgoing(
      Recipient(userId),
      OutMessage(
        Some(
          OutAttachment(
            "template",
            Payload(
             "button",
              text,
              List(
                Button("postback", None, Some(notWhatIWanted), Some(Json.toJson(payload).toString))
              )
            )
          )
        ), None
      )
    )
    Json.toJson(message)
  }
}
