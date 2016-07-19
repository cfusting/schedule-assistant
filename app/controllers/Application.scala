package controllers

import Preamble._
import javax.inject.Inject

import akka.util.ByteString
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.{LoginEvent, Silhouette}
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfileBuilder, SocialProvider, SocialProviderRegistry}
import models._
import models.services.UserService
import play.api._
import play.api.http.HttpEntity
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent._
import ExecutionContext.Implicits.global
import play.api.data.validation.ValidationError
import respond.{ActionResponder, TextResponder}
import silhouette.CookieEnv

class Application @Inject()(ws: WSClient, conf: Configuration, actionResponder: ActionResponder,
                            textResponder: TextResponder, sil: Silhouette[CookieEnv],
                            socialProviderRegistry: SocialProviderRegistry, userService: UserService,
                            authInfoRepository: AuthInfoRepository)
  extends Controller {

  val log = Logger(this.getClass)

  def index = Action {
    Ok(views.html.index())
  }

  def authenticated = sil.SecuredAction { implicit request =>
    Ok(views.html.authenticated())
  }

  def connect(provider: String) = sil.SecuredAction.async { implicit request =>
    (socialProviderRegistry.get[SocialProvider](provider) match {
      case Some(p: SocialProvider with CommonSocialProfileBuilder) =>
        p.authenticate().flatMap {
          case Left(result) => Future.successful(result)
          case Right(authInfo) => for {
            profile <- p.retrieveProfile(authInfo)
            user <- userService.linkProviderToUser(request.identity.loginInfo.head, profile.loginInfo)
            authInfo <- authInfoRepository.save(profile.loginInfo, authInfo)
            result <- Future(Redirect(routes.Application.connected()))
          } yield {
            result
          }
        }
      case _ => Future.failed(new ProviderException(s"Cannot authenticate with unexpected social provider $provider"))
    }).recover {
      case e: ProviderException =>
        log.error("Unexpected provider error", e)
        Redirect(routes.Application.index()).flashing("error" -> "could.not.authenticate")
    }
  }

  def connected = sil.SecuredAction {
    implicit request =>
      Ok("Connected with Facebook!")
  }

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

  private def postback(postback: Postback)(implicit sd: String): Unit = {
    log.debug("Postback")
    val user = Botuser(sd, postback.payload)
    actionResponder.respond(UserAction(user))
  }

}


