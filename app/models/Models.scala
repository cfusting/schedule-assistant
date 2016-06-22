package models

import java.sql.Timestamp

// Incoming
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

// Outgoing
case class Outgoing(recipient: Recipient, message: OutMessage)
case class Recipient(id: String)
case class Button(typ: String, url: Option[String], title: Option[String], payload: Option[String])
case class OutMessage(attachment: Option[OutAttachment], text: Option[String])
//case class TextMessage(text: String)
case class OutAttachment(typ: String, payload: Payload)
case class Payload(template_type: String, text: String, buttons: Seq[Button])

// Tables
case class User(id: String, action: String, timestamp: Option[Timestamp] = None)
