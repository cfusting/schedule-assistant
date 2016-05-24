package controllers

import akka.util.ByteString
import play.api._
import play.api.http.HttpEntity
import play.api.mvc._

class Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def webhook = Action {
    implicit request =>
      val challenge = request.getQueryString("hub.challenge") map { _.trim } getOrElse("")
      val verify = request.getQueryString("hub.verify_token") map { _.trim } getOrElse("")
      if(verify == "penguin_verify") {
        Result(
          header = ResponseHeader(200, Map.empty),
          body = HttpEntity.Strict(ByteString(challenge), Some("text/plain"))
        )
      } else {
        BadRequest(verify)
      }
  }

}