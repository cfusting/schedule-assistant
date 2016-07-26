package controllers

import models._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

object Preamble {

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

  implicit val pageReads: Reads[FacebookPage] = (
    (JsPath \ "access_token").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "id").read[String] and
      (JsPath \ "perms").read[Seq[String]]
    )(FacebookPage.apply _)

  implicit val graphDataReads: Reads[FacebookPageSeq] = (JsPath \ "data").read[Seq[FacebookPage]].map(FacebookPageSeq.apply)

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

  implicit val outMessageWrites: Writes[OutMessage] = (
    (JsPath \ "attachment").writeNullable[OutAttachment] and
      (JsPath \ "text").writeNullable[String]
    )(unlift(OutMessage.unapply _))

  implicit val outgoingWrites: Writes[Outgoing] = (
    (JsPath \ "recipient").write[Recipient] and
      (JsPath \ "message").write[OutMessage]
    )(unlift(Outgoing.unapply _))

}
