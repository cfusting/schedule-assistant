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
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter, ISODateTimeFormat}
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile

import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.{Failure, Success}

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
                 val sd = messaging.sender
                 messaging.delivery match {
                   case Some(delivery) =>
                    Ok("Delivery verified.")
                   case None =>
                 }
                 messaging.message match {
                   case Some(message) => // Message
                     message.text match {
                       case Some(text) =>
                         // Text
                         Logger.info("Text received from: " + sd)
                         userDAO.getUser(sd) onSuccess {
                           case Some(user) =>
                             if (user.action == "day") {
                             Logger.info("Day request.")
                             val dates = getDates(text)
                             sendJson(getTextJson(sd, "Looks like Britt has 1pm - 3pm free on " +
                               dayFormat(dates.head)))
                             userDAO.insertOrUpdate(User(sd, "time")) onComplete {
                               case Success(nothing) =>
                               case Failure(exception) => Logger.error("Failed to insert or update. Id: " + sd + " Action: "
                                 + "time")
                             }
                           }
                         }
                       case None =>
                     }
                   case None =>
                 }
                 messaging.postback match {
                   case Some(postback) => // Postback
                    Logger.info("Postback")
                     postback.payload match {
                       case "schedule" =>
                         sendJson(getTextJson(sd, "What day are you interested in?"))
                         userDAO.insertOrUpdate(User(sd, "day"))
                     }
                   case None =>  // No Postback
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

  def getTextJson(userid: String, text: String): JsValue = {
    val confirmation = Outgoing(
      Recipient(userid),
      OutMessage(
        None, Some(TextMessage(text))
      )
    )
    Json.toJson(confirmation)
  }

  def sendJson(json: JsValue): Future[WSResponse] = {
    Logger.info("Sending json to server: " + json.toString)
     ws.url("https://graph.facebook.com/v2.6/me/messages")
      .withQueryString("access_token" -> conf.underlying.getString("thepenguin.token"))
      .post(json)
  }

  def dayFormat(dt: DateTime) = DateTimeFormat.forPattern("EEEEE MMMM d").print(dt)

}

