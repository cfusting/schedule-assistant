package utilities

import controllers.Preamble._
import models._
import play.api.libs.json.JsValue
import play.api.libs.json._

object JsonUtil {
  def genDayOptions(userid: String): JsValue = {
    val dayOptions = Outgoing(
      Recipient(userid),
      OutMessage(
        Some(OutAttachment(
          "template",
          Payload("button", "What day would you like?", List(
            Button("postback", None, Some("Tomorrow"), Some("tom")),
            Button("postback", None, Some("The day after Tomorrow"), Some("dayafter")),
            Button("postback", None, Some("The day after that"), Some("afterthat"))
          ))
        )), None
      )
    )
    Json.toJson(dayOptions)
  }

  def genTimeOptions(userid: String): JsValue = {
    val timeOptions = Outgoing(
      Recipient(userid),
      OutMessage(
        Some(OutAttachment(
          "template",
          Payload("button", "What time?", List(
            Button("postback", None, Some("1 pm"), Some("1pm")),
            Button("postback", None, Some("2 pm"), Some("2pm")),
            Button("postback", None, Some("3 pm"), Some("3pm"))
          ))
        )), None
      )
    )
    Json.toJson(timeOptions)
  }

  def getMenuJson(implicit sd: String): JsValue = {
    val menu = Outgoing(
      Recipient(sd),
      OutMessage(
        Some(OutAttachment(
          "template",
          Payload("button", "What would you like to do?", List(
            Button("postback", None, Some("Schedule a Lesson."), Some("schedule")),
            Button("postback", None, Some("Cancel a Lesson"), Some("cancel")),
            Button("postback", None, Some("View Lessons"), Some("view"))
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
