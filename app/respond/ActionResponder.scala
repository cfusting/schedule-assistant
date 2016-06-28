package respond

import javax.inject.Inject

import dao.UserDAO
import enums.ActionStates
import google.CalendarTools
import models.{TimeRange, User, UserAction}
import nlp.MasterTime
import org.joda.time.DateTime
import play.Logger
import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSResponse}
import utilities.{JsonUtil, LogUtils, TimeUtils}
import utilities.TimeUtils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ActionResponder @Inject()(userDAO: UserDAO, ws: WSClient, calendar: CalendarTools,
                               configuration: Configuration) {

  val mt = new MasterTime

  def respond(userAction: UserAction): Unit = {
    implicit val userId = userAction.user.id

    ActionStates.withName(userAction.user.action) match {
      case ActionStates.schedule => schedule
      case ActionStates.day => day(userAction.text)
      case ActionStates.time => time(userAction.user, userAction.text)
      case ActionStates.none => none(userAction.text)
      case whatever => default
    }
  }

  private def schedule(implicit userId: String) = {
    sendJson(JsonUtil.getTextMessageJson("What day are you interested in? " +
      "You can say things like \"tomorrow \" or \"next Friday\""))
    userDAO.insertOrUpdate(User(userId, ActionStates.day.toString))
  }

  private def day(text: String)(implicit userId: String) = {
    val dates = mt.parseDateTime(text)
    dates.length match {
      case 1 =>
        val day = TimeUtils.validateDay(dates.head)
        availability(day)

      case default =>
        sendJson(JsonUtil.getTextMessageJson("Sorry I don't understand what day you want. You can say " +
          "things like \"tomorrow\" or \"next Friday\""))
    }
  }

  private def availability(day: DateTime)(implicit userId: String) = {
    Logger.info("User: " + userId + " requested DAY on date: " + TimeUtils.isoFormat(day))
    val times = calendar.getAvailabilityForDay(day)
    Logger.info("Found " + times.size + " availabilities.")
    times.length match {
      case 0 =>
        val response = sendJson(JsonUtil.getTextMessageJson("Britt has no availability on " +
          TimeUtils.dayFormat(day) + ". What other day is good for you?"))
        LogUtils.logSendResult(response)
      case bunch =>
        val response = sendJson(JsonUtil.getTextMessageJson("Britt has the following times available "
          + "on " + TimeUtils.dayFormat(day) + ": " + getTimes(times) + "."))
        LogUtils.logSendResult(response)
        val result = userDAO.insertOrUpdate(User(userId, ActionStates.time.toString,
          Some(day)))
        LogUtils.logDBResult(result)
    }
  }

  private def getTimes(times: Seq[TimeRange]): String = {
    times.map { t =>
      TimeUtils.timeFormat(t.start) + " to " + TimeUtils.timeFormat(t.end)
    } mkString ", "
  }

  private def time(user: User, text: String)(implicit userId: String) = {
    user.timestamp match {
      case Some(day) =>
        val times = mt.parseDateTime(text)
        times.length match {
          case 1 =>
            matchTime(TimeUtils.dateTimeForDayAndTime(day, times.head))
          case default => noOrMultipleTimes
        }
      case None =>
        Logger.info("No Timestamp for user: " + userId)
        bigFail
    }
  }

  private def matchTime(dt: DateTime)(implicit userId: String) = {
    Logger.info("Matching datetime: " + TimeUtils.isoFormat(dt))
    val times = calendar.getAvailabilityForDay(dt)
    TimeUtils.matchTime(dt, times) match {
      case Some(time) =>
        calendar.postAppt(time, userId)
        sendJson(JsonUtil.getTextMessageJson("Ok I've got you down for " +
          TimeUtils.dayFormat(time) + " at " + TimeUtils.timeFormat(time) + "."))
        returnToMenu
      case default =>
        sendJson(JsonUtil.getTextMessageJson("Sorry Britt doesn't have availability " +
          "at " + TimeUtils.timeFormat(dt) + ". Please be more specific."))
    }
  }

  private def returnToMenu(implicit userId: String) = {
    LogUtils.logSendResult(sendJson(JsonUtil.getMenuJson))
    LogUtils.logDBResult(userDAO.insertOrUpdate(User(userId, ActionStates.menu.toString)))
  }

  private def noOrMultipleTimes(implicit userId: String) = {
    sendJson(JsonUtil.getTextMessageJson("Sorry I don't understand what day you want. You can say " +
      "things like \"1pm\" or \"2pm\""))
  }

  private def none(text: String)(implicit userId: String) = {
    Logger.info("No user found in DB, matching raw text for user: " + userId + ". Text: " + text)
    parseTextOptions(text)
  }

  private def parseTextOptions(text: String)(implicit userId: String) = {
    text match {
      case "menu" =>
        Logger.info("User: " + userId + " requested the menu screen.")
        returnToMenu
      case default =>
        Logger.info("No matching text found for user: " + userId)
        val response = sendJson(JsonUtil.getTextMessageJson("Sorry I don't understand what you want. Say \"menu\"" +
          " to " + "see the menu."))
        LogUtils.logSendResult(response)
    }
  }

  private def default(implicit userId: String) = {

  }

  private def sendJson(json: JsValue): Future[WSResponse] = {
    Logger.info("Sending json to server: " + json.toString)
    ws.url(getConf("message.url"))
      .withQueryString("access_token" -> getConf("thepenguin.token"))
      .post(json)
  }

  private def bigFail(implicit sd: String) = {
    Logger.info("Big Fail.")
    val resdb = userDAO.insertOrUpdate(User(sd, ActionStates.menu.toString))
    LogUtils.logDBResult(resdb)
    val res1 = sendJson(JsonUtil.getTextMessageJson("Oops, something has gone wrong... Let's start over."))
    LogUtils.logSendResult(res1)
    val res2 = sendJson(JsonUtil.getMenuJson(sd))
    LogUtils.logSendResult(res2)
  }

  private def getConf(prop: String) = configuration.underlying.getString(prop)

}
