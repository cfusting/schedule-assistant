package ErrorHandlers

import com.mohiva.play.silhouette.api.actions.SecuredErrorHandler
import play.api.mvc.{Controller, RequestHeader, Result}

import scala.concurrent.Future

class CustomSecuredErrorHandler extends SecuredErrorHandler with Controller {

  /**
    * @inheritdoc
    * @param request The request header.
    * @return The result to send to the client.
    */
  override def onNotAuthenticated(implicit request: RequestHeader) = {
    Future.successful(Redirect(controllers.routes.WebApp.login()))
  }

  override def onNotAuthorized(implicit request: RequestHeader): Future[Result] = {
    Future.successful(Redirect(controllers.routes.WebApp.login()))
  }
}
