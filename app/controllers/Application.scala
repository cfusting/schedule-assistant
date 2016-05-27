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

//  case class FMessage(obj: String, entry: Seq[Entry])
//  case class FMessage(obj: String)
//  case class Entry(id: Double, time: Double, messaging: Seq[Messaging])
//  case class Messaging(sender: Double, recipient: Double, message: Message, timestamp: Double)
//  case class Sender(id: Double)
//  case class Recipient(id: Double)
//  case class Message(mid: String, seq: Int, text: String)

//  implicit val fMessagereads: Reads[FMessage] = (
//    (JsPath \ "object").read[String] and
//      (JsPath \ "entry").read[Seq[Entry]]
//    )(FMessage.apply _)

//  implicit val entryReads: Reads[Entry] = (
//    (JsPath \ "id").read[Double] and
//      (JsPath \ "time").read[Double] and
//      (JsPath \ "messaging").read[Seq[Messaging]]
//    )(Entry.apply _)
//
//  implicit val messagingReads: Reads[Messaging] = (
//    (JsPath \ "sender" \ "id").read[Double] and
//      (JsPath \ "recipient" \ "id").read[Double] and
//      (JsPath \ "message").read[Message] and
//      (JsPath \ "timestamp").read[Double]
//    )(Messaging.apply _)

//  implicit val senderReads: Reads[Sender] = (JsPath \ "id").read[Sender]

//  implicit val recipientReads: Reads[Recipient] = (JsPath \ "id").read[Recipient]

//  implicit val messageReads: Reads[Message] = (
//    (JsPath \ "mid").read[String] and
//      (JsPath \ "seq").read[Int] and
//      (JsPath \ "text").read[String]
//    )(Message.apply _)

  def webhookPost = Action(BodyParsers.parse.json) {
    implicit request =>
      Logger.info("request: " + request.body)
      val json: JsValue = request.body
      val userid = (((json \ "entry")(0).get \ "messaging")(0).get \ "sender" \ "id").get
      val dat = Json.obj(
        "recipient" -> Json.obj("id" -> userid),
        "message" -> Json.obj("text" -> "I know wassup!")
      )
      val text = (((json \ "entry")(0).get \ "messaging")(0).get \ "message" \ "text").validate[String]
      text match {
         case s: JsSuccess[String] => {
           Logger.info("Message Recieved: " + s.get)
           val res: Future[WSResponse] = ws.url("https://graph.facebook.com/v2.6/me/messages")
             .withQueryString("access_token" -> conf.underlying.getString("thepenguin.token"))
             .post(dat)
         }
         case e: JsError => Logger.info("Response to sent message.")
       }
      Ok("")
  }

}