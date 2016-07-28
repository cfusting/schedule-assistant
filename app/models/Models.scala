package models

import java.sql.Timestamp

import com.github.tminglei.slickpg.Range
import com.mohiva.play.silhouette.api.LoginInfo
import org.joda.time.DateTime

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

case class OutAttachment(typ: String, payload: Payload)
case class Payload(template_type: String, text: String, buttons: Seq[Button])

// Graph API
case class FacebookPageSeq(data: Seq[FacebookPage])
case class FacebookPage(access_token: String, name: String, id: String, perms: Seq[String])

// Forms
case class FacebookPageForm(pageId: Long)
case class UserOptionsForm(active: Boolean = false, calendarId: String)

// Tables
case class Botuser(id: String, action: String, timestamp: Option[Timestamp] = None,
                   eventId: Option[String] = None, firstName: Option[String] = None,
                   lastName: Option[String] = None)

case class GoogleToFacebookPage(googleLoginInfo: LoginInfo, facebookPageId: Long, accessToken: String, active: Boolean, calendarId: String)

// Actions
case class UserAction(user: Botuser, text: String)

// Time Stuff
case class TimeRange(start: DateTime, end: DateTime)
case class Availability(userId: String, eventId: String, startTime: Timestamp, endTime: Timestamp,
                        userTime: Timestamp)
case class Appointment(eventId: String, times: TimeRange)

// Tokens
case class Tokens(facebookPageToken: String, GoogleAccessToken: String, googleRefreshToken: String)
