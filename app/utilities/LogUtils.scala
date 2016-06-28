package utilities

import play.Logger
import play.api.libs.ws.WSResponse

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object LogUtils {

  def logDBResult(result: Future[Unit])(implicit userId: String) = {
    result onFailure {
      case exception => Logger.error("Failed to insert or update. Id: " + userId + " Exception: " +
        exception.toString)
    }
  }

  def logSendResult(response: Future[WSResponse])(implicit userId: String): Unit = {
    response onComplete {
      case Success(res) => Logger.info("Message to " + userId + " sent successfully with " +
        "response code: " + res.status)
      case Failure(exception) => Logger.info("Message to " + userId + " failed with " +
        "exception: " + exception.getMessage)
    }
  }
}
