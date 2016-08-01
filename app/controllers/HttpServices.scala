package controllers

import JsonConversions._
import javax.inject.Inject

import play.api.data.Forms._
import akka.util.ByteString
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.{LoginEvent, Silhouette}
import com.mohiva.play.silhouette.impl.providers.oauth2.{FacebookProvider, GoogleProvider}
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfileBuilder, OAuth2Info, SocialProvider, SocialProviderRegistry}
import google.CalendarTools
import models._
import models.daos.{BotuserDAO, GoogleToFacebookPageDAO, OAuth2InfoDAO}
import models.services.UserService
import nlp.{DateTimeParser, MasterTime}
import play.api._
import play.api.data.Form
import play.api.http.HttpEntity
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc._

import scala.concurrent._
import ExecutionContext.Implicits.global
import play.api.data.validation.ValidationError
import play.api.i18n.{I18nSupport, MessagesApi}
import respond.{ActionResponder, TextResponder}
import silhouette.CookieEnv

import scala.util.{Failure, Success}


class HttpServices @Inject()(val messagesApi: MessagesApi, ws: WSClient, conf: Configuration, sil: Silhouette[CookieEnv],
                             socialProviderRegistry: SocialProviderRegistry, userService: UserService,
                             authInfoRepository: AuthInfoRepository, googleToFacebookPageDAO: GoogleToFacebookPageDAO,
                             oAuth2InfoDAO: OAuth2InfoDAO, botuserDAO: BotuserDAO, masterTime: DateTimeParser)
  extends Controller with I18nSupport {

  val log = Logger(this.getClass)

  /**
    * Activates the webhook with Facebook.
    */
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

  /**
    * The webhook Facebook calls. All bot interactions start here.
    */
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
    valid.entry foreach {
      entry =>
        (for {
          googleToFacebookPage <- googleToFacebookPageDAO.find(entry.id.toLong)
          oAuth2Info <- oAuth2InfoDAO.find(googleToFacebookPage.googleLoginInfo)
        } yield {
          val calendarTools = new CalendarTools(conf, oAuth2Info.get.accessToken, oAuth2Info.get.refreshToken.get,
            googleToFacebookPage.calendarId)
          entry.messaging.foreach(
            messaging => {
              implicit val userId = messaging.sender
              messaging.delivery foreach {
                delivery
              }
              messaging.message foreach {
                _.text foreach { text =>
                  val textResponder = new TextResponder(conf, ws, botuserDAO, calendarTools,
                    googleToFacebookPage.accessToken, masterTime)
                  textResponder.respond(text)
                }
              }
              messaging.postback foreach { pb =>
                postback(pb, calendarTools, googleToFacebookPage.accessToken)
              }
            }
          )
        }).recover {
          case ex: Exception =>
            log.error(ex.toString)
            InternalServerError("Internal trouble.")
        }
    }
    Ok("Request Received")
  }

  def delivery(delivery: Delivery) = {
    log.debug("Provider verified delivery of messages since: " + delivery.watermark.toString)
    Ok("Delivery confirmation confirmed.")
  }

  /**
    * Handle a Facebook postback event.
    */
  private def postback(postback: Postback, calendarTools: CalendarTools, facebookPageToken: String)(implicit sd:
  String): Unit = {
    log.debug("Postback")
    val user = Botuser(sd, postback.payload)
    val ar = new ActionResponder(botuserDAO, ws, conf, masterTime, calendarTools, facebookPageToken)
    ar.respond(UserAction(user, ""))
  }

}

