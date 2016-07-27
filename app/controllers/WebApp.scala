package controllers

import Preamble._
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

class WebApp @Inject()(val messagesApi: MessagesApi, ws: WSClient, conf: Configuration, sil: Silhouette[CookieEnv],
                       socialProviderRegistry: SocialProviderRegistry, userService: UserService,
                       authInfoRepository: AuthInfoRepository, googleToFacebookPageDAO: GoogleToFacebookPageDAO,
                       oAuth2InfoDAO: OAuth2InfoDAO, botuserDAO: BotuserDAO, masterTime: DateTimeParser)
  extends Controller with I18nSupport {

  val log = Logger(this.getClass)

  /**
    * User starts here with a login screen.
    */
  def login = Action { implicit request =>
    Ok(views.html.login())
  }

  /*
   * Home screen with options for the bot / calendar.
   */
  def home = sil.SecuredAction { implicit request =>
    Ok(views.html.home())
  }

  /**
    * After authorization with Google user is brought here to link with Facebook.
    */
  def authenticated = sil.SecuredAction.async { implicit request =>
    (for {
      loginInfo <- Future(request.identity.loginInfo.find(_.providerID == GoogleProvider.ID).get)
      googleToFacebookPage <- googleToFacebookPageDAO.find(loginInfo)
    } yield googleToFacebookPage match {
      case Some(page) => Ok(views.html.home())
      case None => Ok(views.html.link())
    }) recoverWith {
      case ex: Exception => Future(Ok(views.html.link()))
    }
  }

  /**
    * Handles linking the user with Facebook.
    */
  def link(provider: String) = sil.SecuredAction.async { implicit request =>
    (socialProviderRegistry.get[SocialProvider](provider) match {
      case Some(p: SocialProvider with CommonSocialProfileBuilder) =>
        p.authenticate().flatMap {
          case Left(result) => Future.successful(result)
          case Right(authInfo) => for {
            profile <- p.retrieveProfile(authInfo)
            user <- userService.linkProviderToUser(request.identity.loginInfo.head, profile.loginInfo)
            authInfo <- authInfoRepository.save(profile.loginInfo, authInfo)
            result <- Future(Redirect(routes.WebApp.linked()))
          } yield {
            result
          }
        }
      case _ => Future.failed(new ProviderException(s"Cannot authenticate with unexpected social provider $provider"))
    }).recover {
      case e: ProviderException =>
        log.error("Unexpected provider error", e)
        Redirect(routes.WebApp.login()).flashing("error" -> "could.not.authenticate")
    }
  }

  val form = Form(
    mapping(
      "pageId" -> longNumber
    )(FacebookPageForm.apply)(FacebookPageForm.unapply)
  )

  private def requestFacebookPageList(oAuthInfo: OAuth2Info) = {
    ws.url(conf.underlying.getString("listpages")).withQueryString("fields" -> conf.underlying
      .getString("fields"), "access_token" -> oAuthInfo.accessToken).get()
  }

  private def retrieveFacebookPageList(implicit request: SecuredRequest[CookieEnv, _]): Future[FacebookPageSeq] = {
    for {
      loginInfo <- Future(request.identity.loginInfo.find(_.providerID == FacebookProvider.ID).get)
      oAuthInfo <- userService.retrieveOAuthInfo(loginInfo)
      response <- requestFacebookPageList(oAuthInfo.get)
    } yield {
      response.json.validate[FacebookPageSeq].fold(
        invalid = {
          errors =>
            val details = errors.foreach(x => "field: " + x._1 + ", errors: " + x._2)
            throw new Exception(s"Error validating json for Facebook page sequence: $details")
        },
        valid = {
          x => x
        }
      )
    }
  }

  private def filterFacebookPages(facebookPageSeq: FacebookPageSeq) = {
    facebookPageSeq.data.filter(_.perms.contains("ADMINISTER"))
  }

  /**
    * After linking with Facebook the user is prompted to select a Facebook page with which to associate the bot.
    */
  def linked = sil.SecuredAction.async { implicit request =>
    (for {
      facebookPageSequence <- retrieveFacebookPageList
    } yield {
      val facebookPageOptions = filterFacebookPages(facebookPageSequence).map(x => (x.id, x.name))
      Ok(views.html.page(form, facebookPageOptions))
    }).recover {
      case ex =>
        log.error(s"Unexpected error $ex")
        InternalServerError(s"Exception error: $ex")
    }
  }

  def complete = sil.SecuredAction.async { implicit request =>
    log.info(request.request.body.toString)
    form.bindFromRequest.fold(
      errors => {
        log.error(s"Could not bind form $errors")
        Future.failed(new Exception("Form error."))
      },
      data => {
        (for {
          facebookPageSequence <- retrieveFacebookPageList
          loginInfo <- Future(request.identity.loginInfo.find(_.providerID == GoogleProvider.ID).get)
          accessToken <- Future(filterFacebookPages(facebookPageSequence).filter(_.id.toLong == data.pageId).head
            .access_token)
          response <- subscribeToPage(data.pageId, accessToken)
        } yield {
          if (!(response.json \ "success").get.as[Boolean]) {
            throw new Exception(s"User: ${request.identity.userID} | Failed to subscribe user to facebook page:" +
              s" ${data.pageId}.")
          }
          googleToFacebookPageDAO.insert(GoogleToFacebookPage(loginInfo, data.pageId, accessToken, true, "primary"))
          Redirect(routes.WebApp.home())
        }).recover {
          case ex =>
            log.error(s"Unexpected error $ex")
            InternalServerError(s"Unexpected error: $ex")
        }
      }
    )
  }


  private def subscribeToPage(facebookPageId: Long, accessToken: String)
                             (implicit request: SecuredRequest[CookieEnv, _]) = {
    ws.url(conf.underlying.getString("subscribe"))
      .withQueryString("access_token" -> accessToken)
      .post("")
  }
}
