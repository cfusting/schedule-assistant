package controllers

import java.util
import java.util.Properties

import Preamble._
import javax.inject.Inject

import akka.util.ByteString
import dao.UserDAO
import models._
import play.api._
import play.api.http.HttpEntity
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc._
import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.pipeline._
import edu.stanford.nlp.time._
import edu.stanford.nlp.util.CoreMap
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import scala.concurrent._
import ExecutionContext.Implicits.global

import scala.collection.JavaConversions._
import scala.concurrent.Future

class Application @Inject() (ws: WSClient, conf: Configuration, userDAO: UserDAO) extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def webhook = Action {
    implicit request =>
      val challenge = request.getQueryString("hub.challenge") map { _.trim } getOrElse("")
      val verify = request.getQueryString("hub.verify_token") map { _.trim } getOrElse("")
      if(verify == "penguin_verify") {
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
                 messaging.delivery match {
                   case Some(x) => {
                     // Delivery Confirmation
                    Ok("Delivery verified.")
                   }
                   case None => {

                   }
                 }
                 messaging.message match {
                   case Some(x) => {
                     // Message
                     x.text match {
                       case Some(x) => {
                         // Text
                         Logger.info("Text")
                         val dates = getDates(x)
                         val ires = userDAO.insert(User(messaging.sender, "text"))
                         ires onFailure {
                           case ex => Logger.info("Failed insert: " + ex.toString)
                         }
                         val res = userDAO.all
                         res onFailure {
                           case ex => Logger.info("Failed query: " + ex.toString)

                         }
                         res.map(_.foreach {
                           case user => println(user.id)
                         })
//                         val responseJson = Json.obj(
//                           "recipient" -> Json.obj("id" -> messaging.sender),
//                           "message" -> Json.obj("text" -> "I know wassup!")
//                         )
//                         val res: Future[WSResponse] = ws.url("https://graph.facebook.com/v2.6/me/messages")
//                           .withQueryString("access_token" -> conf.underlying.getString("thepenguin.token"))
//                           .post(responseJson)
                       }
                       case None => {
                         // No text
                       }
                     }
                   }
                   case None => {
                     // No message
                   }
                 }
                 messaging.postback match {
                   case Some(x) => {
                    // Postback
                    Logger.info("Postback")
                     x.payload match {
                       case "schedule" => {
                         val res: Future[WSResponse] = ws.url("https://graph.facebook.com/v2.6/me/messages")
                           .withQueryString("access_token" -> conf.underlying.getString("thepenguin.token"))
                           .post(genDayOptions(messaging.sender))
                       }
                       case "tom" | "dayafter" | "afterthat"  => {
                         val res: Future[WSResponse] = ws.url("https://graph.facebook.com/v2.6/me/messages")
                           .withQueryString("access_token" -> conf.underlying.getString("thepenguin.token"))
                           .post(genTimeOptions(messaging.sender))
                       }
                       case "1pm" | "2pm" | "3pm" => {
                         val res: Future[WSResponse] = ws.url("https://graph.facebook.com/v2.6/me/messages")
                           .withQueryString("access_token" -> conf.underlying.getString("thepenguin.token"))
                           .post(confirmSchedule(messaging.sender))
                       }
                     }
                   }
                   case None => {
                     // No Postback
                   }
                 }
               }
             )
          )
          Ok("Good request.\n")
        }
      )
  }

  def getDates(text: String): Seq[DateTime] = {
    val props = new Properties
    props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref")
    val pipe = new StanfordCoreNLP(props)
    val timeAnnotator = new TimeAnnotator
    pipe.addAnnotator(timeAnnotator)
    val ano = new Annotation(text)
    val theDate = new DateTime
    val fmt = ISODateTimeFormat.dateTime
    ano.set(classOf[CoreAnnotations.DocDateAnnotation], fmt.print(theDate))
    pipe.annotate(ano)
    Logger.info(ano.get(classOf[CoreAnnotations.TextAnnotation]))
    val timexAnsAll = ano.get(classOf[TimeAnnotations.TimexAnnotations])
    timexAnsAll.map(timexAnn => {
      val timeExpr = timexAnn.get(classOf[TimeExpression.Annotation])
      val temporal = timeExpr.getTemporal.getTimexValue
      val nextTime = new DateTime(temporal)
      Logger.info("Parsed date: " + fmt.print(nextTime))
      nextTime
    })
  }

  def genDayOptions(userid: String): JsValue = {
    val dayOptions = Outgoing(
      Recipient(userid),
      OutMessage(
        Some(OutAttachment(
          "template",
          Payload("button", "What day would you like?", List(
            Button("postback", None, Some("Tomorrow"), Some("tom")),
              Button("postback", None, Some("The day after Tomorrow"), Some("dayafter")),
              Button("postback", None, Some("The day after that"), Some("afterthat"))
          ))
        )), None
      )
    )
    Json.toJson(dayOptions)
  }

  def genTimeOptions(userid: String): JsValue = {
    val timeOptions = Outgoing(
      Recipient(userid),
      OutMessage(
        Some(OutAttachment(
          "template",
          Payload("button", "What time?", List(
            Button("postback", None, Some("1 pm"), Some("1pm")),
              Button("postback", None, Some("2 pm"), Some("2pm")),
              Button("postback", None, Some("3 pm"), Some("3pm"))
          ))
        )), None
      )
    )
    Json.toJson(timeOptions)
  }

  def confirmSchedule(userid: String): JsValue = {
    val confirmation = Outgoing(
      Recipient(userid),
      OutMessage(
        None, Some(TextMessage("OK you're all set!"))
      )
    )
    Json.toJson(confirmation)
  }
}