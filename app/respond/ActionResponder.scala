package respond

import javax.inject.Inject

import dao.UserDAO
import enums.ActionStates
import google.CalendarTools
import models.{Availability, TimeRange, User, UserAction}
import nlp.MasterTime
import org.joda.time.DateTime
import play.api.{Configuration, Logger}
import play.api.libs.ws.WSClient
import utilities.{JsonUtil, TimeUtils}
import utilities.TimeUtils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class ActionResponder @Inject()(override val userDAO: UserDAO, override val ws: WSClient,
                                calendar: CalendarTools, override val conf: Configuration,
                                masterTime: MasterTime)
  extends Responder {

  override val log = Logger(this.getClass)

  def respond(userAction: UserAction): Unit = {
    implicit val userId = userAction.user.id
    implicit val log = Logger(this.getClass + " Action: " + userAction.user.action)
    log.debug("User: " + userId + " with Action: " + userAction.user.action)

    ActionStates.withName(userAction.user.action) match {
      case ActionStates.schedule => schedule
      case ActionStates.day => day(userAction.user, userAction.text)
      case ActionStates.duration => duration(userAction.user, userAction.text)
      case ActionStates.time => time(userAction.user, userAction.text)
      case ActionStates.menu => menu
      case ActionStates.notes => notes(userAction.user, userAction.text)
      case whatever => default
    }
  }

  private def schedule(implicit userId: String) = {
    val user = User(userId, ActionStates.day.toString)
    userDAO.insertOrUpdate(user) onSuccess {
      case _ => sendJson(JsonUtil.getTextMessageJson("What day are you interested in? " +
        "You can say things like \"tomorrow \" or \"next Friday\""))
        storeUserName(user)
    }
  }

  private def day(user: User, text: String)(implicit userId: String, log: Logger) = {
    val dates = masterTime.getDateTimes(text)
    dates.length match {
      case 1 => respondWithAvailability(dates.head, user)
      case default =>
        sendJson(JsonUtil.getTextMessageJson("Sorry I don't understand what day you want. You can say " +
          "things like \"tomorrow\" or \"next Friday\""))
    }
  }


  private def respondWithAvailability(day: DateTime, user: User)(implicit userId: String, log: Logger) = {
    val futureDay = TimeUtils.getFutureStartOfDay(day)
    log.debug("User: " + userId + " requested date: " + TimeUtils.isoFormat(futureDay))
    val times = calendar.getAvailabilityForDay(futureDay)
    log.debug("Found " + times.size + " availabilities for user: " + userId)
    times.length match {
      case 0 =>
        sendJson(JsonUtil.getTextMessageJson(s"Britt has no availability " +
          s"on ${TimeUtils.dayFormat(futureDay)}. What other day is good for you?"))
      case bunch =>
        userDAO.insertOrUpdate(User(userId, ActionStates.time.toString, Some(futureDay), user.eventId, user
          .firstName, user.lastName)) onComplete {
          case Success(_) =>
            sendJson(JsonUtil.getTextMessageJson("Britt has the following times available "
              + "on " + TimeUtils.dayFormat(futureDay) + ": " + getTimeStrings(times) + "."))
          case Failure(ex) => bigFail
        }
    }
  }

  private def getTimeStrings(times: Seq[TimeRange]): String = {
    times.map { t =>
      TimeUtils.timeFormat(t.start) + " to " + TimeUtils.timeFormat(t.end)
    } mkString ", "
  }

  private def time(user: User, text: String)(implicit userId: String, log: Logger) = {
    user.timestamp match {
      case Some(day) =>
        val times = masterTime.getDateTimes(text)
        times.length match {
          case 1 =>
            val time = times.head.withDate(day.toLocalDate)
            val avails = calendar.matchAvailabilityForTime(time)
            avails.length match {
              case 0 =>
                sendJson(JsonUtil.getTextMessageJson("Sorry Britt is not available during that time. Try a time " +
                  "during one of the windows specified previously."))
              case 1 =>
                matchAvailability(avails.head, user)
              case bunch =>
                log.error(s"Found multiple (or zero) availabilities $avails.length")
                bigFail
            }
          case default =>
            sendJson(JsonUtil.getTextMessageJson("Sorry I don't understand what day you want. You can say " +
              "things like \"1pm\" or \"2pm\""))
        }
      case None =>
        log.debug(s"No Timestamp for user: $userId")
        bigFail
    }
  }

  private def matchAvailability(avail: Availability, user: User)(implicit userId: String, log: Logger) = {
    userDAO.insertOrUpdate(User(userId, ActionStates.duration.toString,
      Some(avail.userTime), Some(avail.eventId), user.firstName, user.lastName)) onComplete {
      case Success(suc) =>
        sendJson(JsonUtil.getTextMessageJson("Ok. How long a lesson would you like? Britt has at most "
          + TimeUtils.getHourMinutePeriodString(avail.userTime, avail.endTime) + "."))
      case Failure(ex) =>
        log.error("Failed to persist user status. Error: " + ex.getMessage)
        bigFail
    }
  }

  private def duration(user: User, text: String)(implicit userId: String, log: Logger) = {
    user.timestamp match {
      case Some(time) =>
        user.eventId match {
          case Some(eventId) =>
            val times = masterTime.getPeriods(text)
            times.length match {
              case 1 =>
                calendar.scheduleTime(time, times.head, eventId, user.firstName.getOrElse("") + " " + user.lastName
                  .getOrElse(""))
                match {
                  case Some(appt) =>
                    userDAO.insertOrUpdate(User(userId, ActionStates.notes.toString, Some(appt.times.start), Some(appt
                      .eventId), user.firstName, user.lastName)).onComplete {
                      case Success(suc) =>
                        sendJson(JsonUtil.getTextMessageJson("Ok I've got you down for " +
                          TimeUtils.dayFormat(appt.times.start) + " at " + TimeUtils.timeFormat(appt.times.start)
                          + " until " + TimeUtils.timeFormat(appt.times.end) + ". Please enter any additional " +
                          "information (phone number, special instructions) you would like to leave for Britt."))
                      case Failure(ex) =>
                        log.error(s"Failed to persist user to menu action: $ex.getMessage")
                        bigFail
                    }
                  case None =>
                }
              case bunch =>
                log.debug(s"Parsed too many (or no) durations for user: $userId")
                sendJson(JsonUtil.getTextMessageJson("Sorry I don't understand what day you want. You can say " +
                  "things like \"1 hour\" or \"30 minutes\"."))
            }
          case None =>
            log.error(s"No event id for user: $userId")
            bigFail
        }
      case None =>
        log.error(s"No timestamp for user: $userId")
        bigFail
    }
  }

  private def menu(implicit userId: String) = {
    sendJson(JsonUtil.getMenuJson(userId))
  }


  private def notes(user: User, text: String)(implicit userId: String) = {
    user.eventId match {
      case Some(eventId) =>
        calendar.updateEvent(eventId, text)
        sendJson(JsonUtil.getTextMessageJson("Ok Britt will be notified of your lesson and given your notes. Have a" +
          " great day!"))
        returnToMenu
      case None =>
        log.error(s"No event id for user: $userId")
        bigFail
    }
  }

  private def default(implicit userId: String) = {
    returnToMenu
  }

}
