package controllers

import JsonConversions._
import javax.inject.Inject

import play.api.data.Forms._
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.{LoginEvent, Silhouette}
import com.mohiva.play.silhouette.impl.providers.oauth2.{FacebookProvider, GoogleProvider}
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfileBuilder, OAuth2Info, SocialProvider, SocialProviderRegistry}
import google.CalendarTools
import models._
import models.daos.{GoogleToFacebookPageDAO, OAuth2InfoDAO}
import models.services.UserService
import play.api._
import play.api.data.Form
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent._
import ExecutionContext.Implicits.global
import play.api.i18n.{I18nSupport, MessagesApi}
import silhouette.CookieEnv

class WebApp @Inject()(val messagesApi: MessagesApi, ws: WSClient, conf: Configuration, sil: Silhouette[CookieEnv],
                       socialProviderRegistry: SocialProviderRegistry, userService: UserService,
                       authInfoRepository: AuthInfoRepository, googleToFacebookPageDAO: GoogleToFacebookPageDAO,
                       oAuth2InfoDAO: OAuth2InfoDAO)
  extends Controller with I18nSupport {

  val log = Logger(this.getClass)

  /**
    * User starts here with a login screen.
    */
  def login = Action { implicit request =>
    Ok(views.html.login())
  }

  /**
    * Authenticates a user against a social provider.
    *
    * @param provider The ID of the provider to authenticate against.
    * @return The result to display.
    */
  def authenticate(provider: String) = Action.async { implicit request =>
    (socialProviderRegistry.get[SocialProvider](provider) match {
      case Some(p: SocialProvider with CommonSocialProfileBuilder) =>
        p.authenticate().flatMap {
          case Left(result) => Future.successful(result)
          case Right(authInfo) => for {
            profile <- p.retrieveProfile(authInfo)
            user <- userService.save(profile)
            authInfo <- authInfoRepository.save(profile.loginInfo, authInfo)
            authenticator <- sil.env.authenticatorService.create(profile.loginInfo)
            value <- sil.env.authenticatorService.init(authenticator)
            result <- sil.env.authenticatorService.embed(value, Redirect(routes.WebApp.authenticated()))
          } yield {
            sil.env.eventBus.publish(LoginEvent(user, request))
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

  /**
    * After authorization with Google user is brought here to link with Facebook.
    */
  def authenticated = sil.SecuredAction.async { implicit request =>
    (for {
      loginInfo <- Future(request.identity.loginInfo.find(_.providerID == GoogleProvider.ID).get)
      googleToFacebookPage <- googleToFacebookPageDAO.find(loginInfo)
    } yield googleToFacebookPage match {
      case Some(page) => Redirect(routes.WebApp.home())
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

  val optionsForm = Form(
    mapping(
      "pageId" -> longNumber,
      "calendarId" -> text,
      "name" -> text(minLength = 2),
      "eventNoun" -> text(minLength = 2)
    )(UserOptionsForm.apply)(UserOptionsForm.unapply)
  )

  /**
    * After linking with Facebook the user is sets options.
    */
  def linked = sil.SecuredAction.async { implicit request =>
    for {
      facebookPageSequence <- retrieveFacebookPageList
      loginInfo <- Future(request.identity.loginInfo.find(_.providerID == GoogleProvider.ID).get)
      authInfo <- userService.retrieveOAuthInfo(loginInfo)
      ai = authInfo.get
      calendarList <- new CalendarTools(conf, ai.accessToken, ai.refreshToken.get, "primary").getCalendarList
    } yield {
      val facebookPageOptions = filterFacebookPages(facebookPageSequence).map(x => (x.id, x.name))
      Ok(views.html.options(optionsForm, facebookPageOptions, calendarList))
    }
  }

  /**
    * After setting options the Facebook page is subscribed to the bot and the
    * page refresh token is stored.
    */
  def postOptions = sil.SecuredAction.async { implicit request =>
    optionsForm.bindFromRequest.fold(
      formWithErrors => {
        for {
          facebookPageSequence <- retrieveFacebookPageList
          loginInfo <- Future(request.identity.loginInfo.find(_.providerID == GoogleProvider.ID).get)
          authInfo <- userService.retrieveOAuthInfo(loginInfo)
          ai = authInfo.get
          calendarList <- new CalendarTools(conf, ai.accessToken, ai.refreshToken.get, "primary").getCalendarList
        } yield {
          val facebookPageOptions = filterFacebookPages(facebookPageSequence).map(x => (x.id, x.name))
          BadRequest(views.html.options(formWithErrors, facebookPageOptions, calendarList))
        }
      },
      data => {
        for {
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
          // Note if this data is updated the active field is NOT mutated.
          googleToFacebookPageDAO.save(GoogleToFacebookPage(loginInfo, data.pageId, accessToken, true,
            data.calendarId, data.name, data.eventNoun.toLowerCase))
          Redirect(routes.WebApp.home())
        }
      }
    )
  }

  def options = sil.SecuredAction.async { implicit request =>
    for {
      facebookPageSequence <- retrieveFacebookPageList
      loginInfo <- Future(request.identity.loginInfo.find(_.providerID == GoogleProvider.ID).get)
      gtfp <- googleToFacebookPageDAO.find(loginInfo)
      goo = gtfp.get
      authInfo <- userService.retrieveOAuthInfo(loginInfo)
      ai = authInfo.get
      calendarList <- new CalendarTools(conf, ai.accessToken, ai.refreshToken.get, goo.calendarId).getCalendarList
    } yield {
      val facebookPageOptions = filterFacebookPages(facebookPageSequence).map(x => (x.id, x.name))
      val filledForm = optionsForm.fill(UserOptionsForm(goo.facebookPageId, goo.calendarId, goo.name, goo.eventNoun))
      Ok(views.html.options(filledForm, facebookPageOptions, calendarList))
    }
  }

  private def subscribeToPage(facebookPageId: Long, accessToken: String)
                             (implicit request: SecuredRequest[CookieEnv, _]) = {
    ws.url(conf.underlying.getString("subscribe"))
      .withQueryString("access_token" -> accessToken)
      .post("")
  }


  val homeForm = Form(
    mapping(
      "active" -> boolean
    )(HomeForm.apply)(HomeForm.unapply)
  )

  /*
   * Home screen with options for the bot / calendar.
   */
  def home = sil.SecuredAction.async { implicit request =>
    for {
      loginInfo <- Future(request.identity.loginInfo.find(_.providerID == GoogleProvider.ID).get)
      gtfp <- googleToFacebookPageDAO.find(loginInfo)
    } yield {
      val filledForm = homeForm.fill(HomeForm(active = gtfp.get.active))
      Ok(views.html.home(filledForm))
    }
  }

  def postActive = sil.SecuredAction { implicit request =>
    homeForm.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.home(formWithErrors))
      },
      data => {
        for {
          loginInfo <- Future(request.identity.loginInfo.find(_.providerID == GoogleProvider.ID).get)
        } yield googleToFacebookPageDAO.update(loginInfo, data.active)
        Redirect(routes.WebApp.home())
      }
    )
  }
}
