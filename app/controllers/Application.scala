package controllers

import java.sql.Timestamp

import utilities._
import enums._
import Preamble._
import javax.inject.Inject

import akka.util.ByteString
import dao.UserDAO
import models._
import nlp.MasterTime
import org.joda.time.DateTime
import play.api._
import play.api.http.HttpEntity
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc._

import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.{Failure, Success}

class Application @Inject()(ws: WSClient, conf: Configuration, userDAO: UserDAO) extends Controller {

  val mt = new MasterTime

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def webhook = Action {
    implicit request =>
      Logger.info("request: " + request.body)
      val challenge = request.getQueryString("hub.challenge") map {
        _.trim
      } getOrElse ("")
      val verify = request.getQueryString("hub.verify_token") map {
        _.trim
      } getOrElse ("")
      if (verify == "penguin_verify") {
        Logger.info("Penguin verified.")
        Result(
          header = ResponseHeader(200, Map.empty),
          body = HttpEntity.Strict(ByteString(challenge), Some("text/plain"))
        )
      } else {
        BadRequest(verify)
      }
  }

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
                  val sd = messaging.sender
                  messaging.delivery match {
                    case Some(delivery) => handleDelivery(delivery)
                    case None =>
                  }
                  messaging.message match {
                    case Some(message) =>
                      message.text match {
                        case Some(text) => handleText(text, sd)
                        case None =>
                      }
                    case None =>
                  }
                  messaging.postback match {
                    case Some(postback) => handlePostback(postback, sd)
                    case None =>
                  }
                }
              )
          )
          Ok("Good request.\n")
        }
      )
  }

  private def handlePostback(postback: Postback, sd: String): Unit = {
    Logger.info("Postback")
    ActionStates.withName(postback.payload) match {
      case ActionStates.schedule =>
        sendJson(JsonUtil.getTextMessageJson(sd, "What day are you interested in? " +
          "You can say things like \"tomorrow \" or \"next Friday\""))
        userDAO.insertOrUpdate(User(sd, ActionStates.day.toString))
    }
  }

  private def handleText(text: String, sd: String): Unit = {
    Logger.info("Text message received from: " + sd)
    userDAO.getUser(sd) onComplete {
      case Success(suc) => suc match {
        case Some(user) =>
          respondToAction(user, text)
        case None =>
          respondToTextNoUser(text, sd)
      }
      case Failure(ex) =>
        // Assume a failure implies the user has not yet been added to the database
        // TODO: don't make this assumption
        Logger.info("DB failure for user with id: " + sd + ". Message: " + ex.getMessage.toString)
        val result = userDAO.insertOrUpdate(User(sd, ActionStates.menu.toString))
        logDBResult(result, sd)
    }
  }

  def handleDelivery(delivery: Delivery) = {
    delivery.mids match {
      case Some(ids) => ids foreach {
        id => Logger.info("Provider verified delivery of message: " + id.toString)
      }
      case None => Logger.info("Provider verified delivery of message(s) with unknown ids.")
    }
    Ok("Roger that!")
  }

  private def respondToAction(user: User, text: String) = {
    Logger.info("User: " + user.id + " found in DB. Matching action status to determine response.")
    ActionStates.withName(user.action) match {
      case ActionStates.day =>
        val dates = mt.getDates(text)
        dates.length match {
          case 0 =>
            sendJson(JsonUtil.getTextMessageJson(user.id, "Sorry I don't understand what day you want. You can say " +
              "things like \"tomorrow\" or \"next Friday\""))
          case 1 => Logger.info("User: " + user.id + " requested DAY on date: " + TimeUtils.isoFormat(dates.head))
            val response =
              sendJson(JsonUtil.getTextMessageJson(user.id, "Looks like Britt has 1pm - 3pm free on " +
                TimeUtils.dayFormat(dates.head) + ". What time would you like?"))
            logSendResult(response, user.id)
            val result = userDAO.insertOrUpdate(User(user.id, ActionStates.time.toString, Some(new Timestamp(dates.head
              .getMillis))))
            logDBResult(result, user.id)
          case default =>
            sendJson(JsonUtil.getTextMessageJson(user.id, "Sorry I don't understand what day you want. You can say " +
              "things like \"tomorrow\" or \"next Friday\""))
        }
      case ActionStates.time =>
        user.timestamp match {
          case Some(timestamp) =>
            val dates = mt.getDates(text)
            dates.length match {
              case 0 =>
                sendJson(JsonUtil.getTextMessageJson(user.id, "Sorry I don't understand what day you want. You can say " +
                  "things like \"1pm\" or \"2pm\""))
              case 1 =>
                val scheduled = dates.head.withDayOfYear(new DateTime(timestamp).getDayOfYear)
                Logger.info("User: " + user.id + " requested TIME on date: " + TimeUtils.isoFormat(dates.head))
                val response = sendJson(JsonUtil.getTextMessageJson(user.id, "Ok I've got you down for one hour starting at " +
                  TimeUtils.timeFormat(scheduled) + " on " + TimeUtils.dayFormat(scheduled) + ". Say \"menu\" to return to " +
                  "the main menu."))
                logSendResult(response, user.id)
                val result = userDAO.insertOrUpdate(User(user.id, ActionStates.complete.toString, Some(new Timestamp(
                  scheduled.getMillis))))
                logDBResult(result, user.id)
              case default =>
                sendJson(JsonUtil.getTextMessageJson(user.id, "Sorry I don't understand what day you want. You can say " +
                  "things like \"1pm\" or \"2pm\""))
            }
          case None =>
            bigFail(user.id)
        }
      case default =>
        Logger.info("No matching Action found for user: " + user.id)
        parseTextOptions(text, user.id)
    }
  }

  private def parseTextOptions(text: String, sd: String) = {
    text match {
      case "menu" =>
        Logger.info("User: " + sd + " requested the menu screen.")
        val result = sendJson(JsonUtil.getMenuJson(sd))
        logSendResult(result, sd)
      case default =>
        Logger.info("No matching text found for user: " + sd)
        val response = sendJson(JsonUtil.getTextMessageJson(sd, "Sorry I don't understand what you want. Say \"menu\"" +
          " to " + "see the menu."))
        logSendResult(response, sd)
        val result = userDAO.insertOrUpdate(User(sd, ActionStates.menu.toString))
        logDBResult(result, sd)
    }
  }

  private def respondToTextNoUser(text: String, sd: String) = {
    Logger.info("No user found in DB, matching raw text for user: " + sd + ". Text: " + text)
    parseTextOptions(text, sd)
  }

  private def logDBResult(result: Future[Unit], sd: String) = {
    result onComplete {
      case Success(nothing) =>
      case Failure(exception) => Logger.error("Failed to insert or update. Id: " + sd + " Action: "
        + "time")
    }
  }

  private def logSendResult(response: Future[WSResponse], sd: String): Unit = {
    response onComplete {
      case Success(res) => Logger.info("Message to " + sd + " sent successfully with " +
        "response code: " + res.status)
      case Failure(exception) => Logger.info("Message to " + sd + " failed with " +
        "exception: " + exception.getMessage)
    }
  }

  private def sendJson(json: JsValue): Future[WSResponse] = {
    Logger.info("Sending json to server: " + json.toString)
    ws.url("https://graph.facebook.com/v2.6/me/messages")
      .withQueryString("access_token" -> conf.underlying.getString("thepenguin.token"))
      .post(json)
  }

  private def bigFail(sd: String) = {
    Logger.info("Big Fail.")
    val resdb = userDAO.insertOrUpdate(User(sd, ActionStates.menu.toString))
    logDBResult(resdb, sd)
    val res1 = sendJson(JsonUtil.getTextMessageJson(sd, "Something has gone terribly wrong! Let's start over."))
    logSendResult(res1, sd)
    val res2 = sendJson(JsonUtil.getMenuJson(sd))
    logSendResult(res2, sd)
  }
}


