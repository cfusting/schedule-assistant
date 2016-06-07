package controllers

import javax.inject.Inject

import akka.util.ByteString
import play.api._
import play.api.http.HttpEntity
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import scala.concurrent.Future

class Application @Inject() (ws: WSClient, conf: Configuration) extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def webhook = Action {
    implicit request =>
      val challenge = request.getQueryString("hub.challenge") map { _.trim } getOrElse("")
      val verify = request.getQueryString("hub.verify_token") map { _.trim } getOrElse("")
      if(verify == "penguin_verify") {
        Logger.info("Penguin verified.")
        Result(
          header = ResponseHeader(200, Map.empty),
          body = HttpEntity.Strict(ByteString(challenge), Some("text/plain"))
        )
      } else {
        BadRequest(verify)
      }
  }

  case class FMessage(obj: String, entry: Seq[Entry])
  case class Entry(id: String, time: Double, messaging: Seq[Messaging])
  case class Messaging(sender: String, recipient: String, message: Option[Message],
                       timestamp: Option[Double], delivery: Option[Delivery],
                       postback: Option[Postback])
  case class Message(mid: String, seq: Int, text: Option[String],
                     attachments: Option[Seq[Attachment]])
  case class Attachment(typ: String, payloadUrl: String)
  case class Delivery(mids: Option[Seq[String]], watermark: Double, seq: Int)
  case class Postback(payload: String)

  implicit val postbackWrites = new Writes[Postback] {
    def writes(postback: Postback) = Json.obj(
      "payload" -> postback.payload
    )
  }

  implicit val deliveryWrites: Writes[Delivery] = (
    (JsPath \ "mids").writeNullable[Seq[String]] and
      (JsPath \ "watermark").write[Double] and
      (JsPath \ "seq").write[Int]
    )(unlift(Delivery.unapply _))

  implicit val attachmentWrites: Writes[Attachment] = (
    (JsPath \ "type").write[String] and
      (JsPath \ "payload" \ "url").write[String]
    )(unlift(Attachment.unapply _))

  implicit val messageWrites: Writes[Message] = (
    (JsPath \ "mid").write[String] and
      (JsPath \ "seq").write[Int] and
      (JsPath \ "text").writeNullable[String] and
      (JsPath \ "attachments").writeNullable[Seq[Attachment]]
    )(unlift(Message.unapply _))

  implicit val messagingWrites: Writes[Messaging] = (
  (JsPath \ "sender" \ "id").write[String] and
    (JsPath \ "recipient" \ "id").write[String] and
    (JsPath \ "message").writeNullable[Message] and
    (JsPath \ "timestamp").writeNullable[Double] and
    (JsPath \ "delivery").writeNullable[Delivery] and
    (JsPath \ "postback").writeNullable[Postback]
  )(unlift(Messaging.unapply _))

  implicit val entryWrites: Writes[Entry] = (
  (JsPath \ "id").write[String] and
    (JsPath \ "time").write[Double] and
    (JsPath \ "messaging").write[Seq[Messaging]]
  )(unlift(Entry.unapply _))
  
  implicit val fMessageWrites: Writes[FMessage] = (
  (JsPath \ "object").write[String] and
    (JsPath \ "entry").write[Seq[Entry]]
  )(unlift(FMessage.unapply _))

  implicit val postbackReads: Reads[Postback] = (JsPath \ "payload").read[String].map(Postback.apply)
  
  implicit val deliveryReads: Reads[Delivery] = (
    (JsPath \ "mids").readNullable[Seq[String]] and
      (JsPath \ "watermark").read[Double] and
      (JsPath \ "seq").read[Int]
    )(Delivery.apply _)

  implicit val attachmentReads: Reads[Attachment] = (
    (JsPath \ "type").read[String] and
      (JsPath \ "payload" \ "url").read[String]
    )(Attachment.apply _)

  implicit val messageReads: Reads[Message] = (
    (JsPath \ "mid").read[String] and
      (JsPath \ "seq").read[Int] and
      (JsPath \ "text").readNullable[String] and
      (JsPath \ "attachments").readNullable[Seq[Attachment]]
    )(Message.apply _)

  implicit val messagingReads: Reads[Messaging] = (
  (JsPath \ "sender" \ "id").read[String] and
    (JsPath \ "recipient" \ "id").read[String] and
    (JsPath \ "message").readNullable[Message] and
    (JsPath \ "timestamp").readNullable[Double] and
    (JsPath \ "delivery").readNullable[Delivery] and
    (JsPath \ "postback").readNullable[Postback]
  )(Messaging.apply _)

  implicit val entryReads: Reads[Entry] = (
  (JsPath \ "id").read[String] and
    (JsPath \ "time").read[Double] and
    (JsPath \ "messaging").read[Seq[Messaging]]
  )(Entry.apply _)
  
  implicit val fMessagereads: Reads[FMessage] = (
  (JsPath \ "object").read[String] and
    (JsPath \ "entry").read[Seq[Entry]]
  )(FMessage.apply _)

  def webhookPost = Action(BodyParsers.parse.json) {
    implicit request =>
      Logger.info("request: " + request.body)
      val json: JsValue = request.body
      val fmessageResult = json.validate[FMessage]
      fmessageResult.fold(
        errors => {
          Logger.error(errors.toString())
          BadRequest("Bad request. Errors: " + errors)
        },
        fmessage => {
          fmessage.entry.foreach(
            entry =>
             entry.messaging.foreach(
               messaging => {
                 messaging.delivery match {
                   case Some(x) => {
                     // Delivery Confirmation
                    Ok("Delivery verified.")
                   }
                   case None => {

                   }
                 }
                 messaging.message match {
                   case Some(x) => {
                     // Message
                     x.text match {
                       case Some(x) => {
                         // Text
                         Logger.info("Text")
                         val responseJson = Json.obj(
                           "recipient" -> Json.obj("id" -> messaging.sender),
                           "message" -> Json.obj("text" -> "I know wassup!")
                         )
                         val res: Future[WSResponse] = ws.url("https://graph.facebook.com/v2.6/me/messages")
                           .withQueryString("access_token" -> conf.underlying.getString("thepenguin.token"))
                           .post(responseJson)
                       }
                       case None => {
                         // No text
                       }
                     }
                   }
                   case None => {
                     // No message
                   }
                 }
                 messaging.postback match {
                   case Some(x) => {
                    // Postback
                    Logger.info("Postback")
                     x.payload match {
                       case "schedule" => {
                         val res: Future[WSResponse] = ws.url("https://graph.facebook.com/v2.6/me/messages")
                           .withQueryString("access_token" -> conf.underlying.getString("thepenguin.token"))
                           .post(genDayOptions(messaging.sender))
                       }
                       case "tom" =>
                       case "dayafter" =>
                       case "afterthat" =>
                     }
                   }
                   case None => {
                     // No Postback
                   }
                 }
               }
             )
          )
          Ok("Good request.\n")
        }
      )
  }

  case class Outgoing(recipient: Recipient, message: OutMessage)
  case class Recipient(id: String)
  case class Payload(template_type: String, text: String, buttons: Seq[Button])
  case class Button(typ: String, url: Option[String], title: Option[String], payload: Option[String])
  case class OutMessage(attachment: OutAttachment)
  case class OutAttachment(typ: String, payload: Payload)

  implicit val recipientWrites = new Writes[Recipient] {
    def writes(recipient: Recipient) = Json.obj(
      "id" -> recipient.id
    )
  }

  implicit val buttonWrites: Writes[Button] = (
    (JsPath \ "type").write[String] and
      (JsPath \ "url").writeNullable[String] and
      (JsPath \ "title").writeNullable[String] and
      (JsPath \ "payload").writeNullable[String]
    )(unlift(Button.unapply _))

  implicit val payloadWrites: Writes[Payload] = (
    (JsPath \ "template_type").write[String] and
      (JsPath \ "text").write[String] and
      (JsPath \ "buttons").write[Seq[Button]]
    )(unlift(Payload.unapply _))

  implicit val outAttachmentWrites: Writes[OutAttachment] = (
    (JsPath \ "type").write[String] and
      (JsPath \ "payload").write[Payload]
    )(unlift(OutAttachment.unapply _))

  implicit val outMessageWrites = new Writes[OutMessage] {
    def writes(outMessage: OutMessage) = Json.obj(
      "attachment" -> outMessage.attachment
    )
  }

  implicit val outgoingWrites: Writes[Outgoing] = (
    (JsPath \ "recipient").write[Recipient] and
      (JsPath \ "message").write[OutMessage]
    )(unlift(Outgoing.unapply _))

  def genDayOptions(userid: String): JsValue = {
    val dayOptions = Outgoing(
      Recipient(userid),
      OutMessage(
        OutAttachment(
          "template",
          Payload("button", "What day would you like?", List(
            Button("postback", None, Some("Tomorrow"), Some("tom")),
              Button("postback", None, Some("The day after Tomorrow"), Some("dayafter")),
              Button("postback", None, Some("The day after that"), Some("afterthat"))
//              Button("postback", None, Some("Thursday"), Some("thur"))
//              Button("postback", None, Some("Friday"), Some("fri")),
//              Button("postback", None, Some("Saturday"), Some("sat")),
//              Button("postback", None, Some("Sunday"), Some("sun"))
          ))
        )
      )
    )
    Json.toJson(dayOptions)
  }
}