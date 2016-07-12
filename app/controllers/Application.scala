package controllers

import enums._
import Preamble._
import javax.inject.Inject

import akka.util.ByteString
import models.dao.UserDAO
import models._
import play.api._
import play.api.http.HttpEntity
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc._

import scala.concurrent._
import ExecutionContext.Implicits.global
import play.api.data.validation.ValidationError
import respond.{ActionResponder, TextResponder}

class Application @Inject()(ws: WSClient, conf: Configuration, userDAO: UserDAO,
                            actionResponder: ActionResponder, textResponder: TextResponder)
  extends Controller {

  val log = Logger(this.getClass)

  def webhook = Action {
    implicit request =>
      log.info("request: " + request.body)
      val challenge = request.getQueryString("hub.challenge") map {
        _.trim
      } getOrElse ""
      val verify = request.getQueryString("hub.verify_token") map {
        _.trim
      } getOrElse ""
      if (verify == "penguin_verify") {
        log.info("Penguin verified.")
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
      log.debug("request: " + request.body)
      val fmessageResult = request.body.validate[FMessage]
      fmessageResult.fold(handleJsonErrors, handleJsonSuccess)
  }

  private def handleJsonErrors(invalid: Seq[(JsPath, Seq[ValidationError])]): Result = {
    log.error(invalid.toString())
    BadRequest("Malformed JSON.")
  }

  private def handleJsonSuccess(valid: FMessage): Result = {
    valid.entry.foreach(
      entry =>
        entry.messaging.foreach(
          messaging => {
            implicit val userId = messaging.sender
            messaging.delivery foreach {
              delivery
            }
            messaging.message foreach {
              _.text foreach textResponder.respond
            }
            messaging.postback foreach {
              postback
            }
          }
        )
    )
    Ok("Request Received")
  }

  def delivery(delivery: Delivery) = {
    log.debug("Provider verified delivery of messages since: " + delivery.watermark.toString)
    Ok("Delivery confirmation confirmed.")
  }

  private def postback(postback: Postback )(implicit sd: String): Unit = {
    log.debug("Postback")
    val user = User(sd, postback.payload)
    actionResponder.respond(UserAction(user))
  }

}


