package controllers

import java.sql.Timestamp

import utilities._
import enums._
import Preamble._
import javax.inject.Inject

import akka.util.ByteString
import dao.{AvailabilityDAO, MessagesDAO, UserDAO}
import google.CalendarTools
import models._
import nlp.MasterTime
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
import play.api.data.validation.ValidationError
import respond.ActionResponder

class Application @Inject()(ws: WSClient, conf: Configuration, userDAO: UserDAO,
                            actionResponder: ActionResponder, messagesDAO: MessagesDAO)
  extends Controller {

  val mt = new MasterTime
  val calendar = new CalendarTools(conf)

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def webhook = Action {
    implicit request =>
      Logger.info("request: " + request.body)
      val challenge = request.getQueryString("hub.challenge") map {
        _.trim
      } getOrElse ""
      val verify = request.getQueryString("hub.verify_token") map {
        _.trim
      } getOrElse ""
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
      val fmessageResult = request.body.validate[FMessage]
      fmessageResult.fold(handleJsonErrors, handleJsonSuccess)
  }

  private def handleJsonErrors(invalid: Seq[(JsPath, Seq[ValidationError])]): Result = {
    Logger.error(invalid.toString())
    BadRequest("Malformed JSON.")
  }

  private def handleJsonSuccess(valid: FMessage): Result = {
    valid.entry.foreach(
      entry =>
        entry.messaging.foreach(
          messaging => {
            implicit val userId = messaging.sender
            messaging.delivery foreach {
              handleDelivery
            }
            messaging.message foreach {
              _.text foreach handleText
            }
            messaging.postback foreach {
              handlePostback
            }
          }
        )
    )
    Ok("Request Received")
  }

  def handleDelivery(delivery: Delivery) = {
    Logger.info("Provider verified delivery of messages since: " + delivery.watermark.toString)
    Ok("Delivery confirmation confirmed.")
  }

  private def handleText(text: String)(implicit sd: String): Unit = {
    Logger.info("Text message received from: " + sd)
    userDAO.getUser(sd) onComplete {
      case Success(suc) => suc match {
        case Some(user) =>
          actionResponder.respond(UserAction(user, text))
        case None =>
          val user = User(sd, ActionStates.none.toString)
          val dbResult = userDAO.insertOrUpdate(user)
          LogUtils.logDBResult(dbResult)
          actionResponder.respond(UserAction(user, text))
      }
      case Failure(ex) =>
        Logger.info("DB failure for user with id: " + sd + ". Message: " + ex.getMessage.toString)
        bigFail
    }
  }

  private def handlePostback(postback: Postback )(implicit sd: String): Unit = {
    Logger.info("Postback")
    val user = User(sd, ActionStates.schedule.toString)
    actionResponder.respond(UserAction(user))
  }

  private def sendJson(json: JsValue): Future[WSResponse] = {
    Logger.info("Sending json to server: " + json.toString)
    ws.url("https://graph.facebook.com/v2.6/me/messages")
      .withQueryString("access_token" -> conf.underlying.getString("thepenguin.token"))
      .post(json)
  }

  private def bigFail(implicit sd: String) = {
    Logger.info("Big Fail.")
    val resdb = userDAO.insertOrUpdate(User(sd, ActionStates.menu.toString))
    LogUtils.logDBResult(resdb)
    val res1 = sendJson(JsonUtil.getTextMessageJson("Something has gone terribly wrong! Let's start over."))
    LogUtils.logSendResult(res1)
    val res2 = sendJson(JsonUtil.getMenuJson(sd))
    LogUtils.logSendResult(res2)
  }
}


