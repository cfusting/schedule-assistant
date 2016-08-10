package respond

import models.daos.BotuserDAO
import enums.{ActionStates, DataLogs}
import google.CalendarTools
import models._
import nlp.DateTimeParser
import org.joda.time.DateTime
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.{Configuration, Logger}
import play.api.libs.ws.WSClient
import utilities.{JsonUtil, TimeUtils}
import utilities.TimeUtils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class ActionResponder(override val userDAO: BotuserDAO, override val ws: WSClient,
                      override val conf: Configuration, masterTime: DateTimeParser,
                      override val calendarTools: CalendarTools, override val gtfp: GoogleToFacebookPage,
                      override val messagesApi: MessagesApi)(implicit val lang: Lang)
  extends Responder {

  override val log = Logger(this.getClass)
  override var prefix = ""

  def respond(userAction: UserAction): Unit = {
    implicit val userId = userAction.user.id
    // log table def is now class, user id, user action, text
    prefix = s"||$userId||${userAction.text}||${userAction.user.action}"
    log.info(prefix)

    ActionStates.withName(userAction.user.action) match {
      case ActionStates.schedule => schedule
      case ActionStates.day => day(userAction.user, userAction.text)
      case ActionStates.time => time(userAction.user, userAction.text)
      case ActionStates.duration => duration(userAction.user, userAction.text)
      case ActionStates.menu => menu
      case ActionStates.notes => notes(userAction.user, userAction.text)
      case ActionStates.cancel => cancel
      case ActionStates.cancelDateTime => cancelDateTime(userAction.text)
      case ActionStates.view => view
      case ActionStates.notWhatIWanted => notWhatIWanted(userAction.returnToAction.get, userAction.user)
      case whatever => default
    }
  }

  private def schedule(implicit userId: String) = {
    val user = Botuser(userId, ActionStates.day.toString)
    (for {
      _ <- userDAO.insertOrUpdate(user)
      _ <- storeUserName(user)
    } yield {
      sendJson(JsonUtil.getTextMessageJson(Messages("ar.day.ask")))
    }).recover {
      case NonFatal(ex) =>
        log.error(s"$prefix||${ex.toString}")
        bigFail
    }
  }

  private def day(user: Botuser, text: String)(implicit userId: String) = {
    val dates = masterTime.getDatetimes(text)
    dates.length match {
      case 1 =>
        respondWithAvailability(dates.head, user, text)
      case default =>
        sendJson(JsonUtil.getTextMessageJson(Messages("ar.day.repeat")))
    }
  }

  private def respondWithAvailability(day: DateTime, user: Botuser, text: String)
                                     (implicit userId: String) = {
    val futureDay = TimeUtils.getFutureStartOfDay(day)
    (for {
      times <- calendarTools.getAvailabilityForDay(futureDay)
    } yield {
      log.debug(s"$prefix||Found ${times.size} availabilities")
      times.length match {
        case 0 =>
          sendJson(JsonUtil.getTextMessageJson(Messages("ar.day.noavail", gtfp.name, TimeUtils.dayFormat(futureDay))))
        case bunch =>
          userDAO.insertOrUpdate(user.copy(action = ActionStates.time.toString, startTime = Some(futureDay), message
            = Some(text)))
          sendJson(JsonUtil.getTextMessageWithNotWhatIWantedJson(Messages("ar.day.avail", gtfp.name,
            TimeUtils.dayFormat(futureDay), getTimeRangeStrings(times)), Messages("ar.day.wrong"),
            BotPayload(ActionStates.notWhatIWanted.toString, Some(ActionStates.schedule.toString))))
      }
    }).recover {
      case NonFatal(ex) =>
        log.error(s"$prefix||${ex.toString}")
        bigFail
    }
  }

  private def time(user: Botuser, text: String)(implicit userId: String) = {
    user.startTime match {
      case Some(day) =>
        val timeString = TimeUtils.getReadableTimeString(text)
        val times = masterTime.getTimes(timeString)
        times.length match {
          case t if t > 0 && t <= 2 =>
            val properTimes = times.map(_.withDate(day.toLocalDate))
            val avails = calendarTools.matchAvailabilityForTime(properTimes)
            avails.length match {
              case 0 =>
                sendJson(JsonUtil.getTextMessageJson(Messages("ar.time.noavail", gtfp.name)))
              case 1 =>
                matchAvailability(avails.head, user, text)
              case bunch =>
                log.error(s"$prefix||Found multiple (or zero) availabilities $avails.length")
                sendJson(JsonUtil.getTextMessageJson(Messages("ar.time.repeat")))
            }
          case default =>
            sendJson(JsonUtil.getTextMessageJson(Messages("ar.time.repeat")))
        }
      case None =>
        log.debug(s"No Timestamp for user: $userId")
        bigFail
    }
  }

  private def matchAvailability(avail: Availability, user: Botuser, text: String)
                               (implicit userId: String) = {
    (for {
      _ <- userDAO.insertOrUpdate(user.copy(action = ActionStates.duration.toString, startTime = Some(avail.userTime),
        eventId = Some(avail.eventId), message = Some(text)))
    } yield {
      sendJson(JsonUtil.getTextMessageWithNotWhatIWantedJson(Messages("ar.time.match",
        TimeUtils.timeFormat(new DateTime(avail.userTime)),
        gtfp.eventNoun, gtfp.name, TimeUtils.getHourMinutePeriodString(avail.userTime, avail.endTime)),
        Messages("ar.time.wrong"), BotPayload(ActionStates.notWhatIWanted.toString, Some(ActionStates.day.toString))))
    }).recover {
      case NonFatal(ex) =>
        log.error(s"$prefix||${ex.getMessage}")
        bigFail
    }
  }

  private def duration(user: Botuser, text: String)(implicit userId: String) = {
    val times = masterTime.getDurations(TimeUtils.getReadableDurationString(text))
    times.length match {
      case t if t <= 2 && t > 0 =>
        val duration = times.reduceLeft(_.plus(_))
        val endTime = user.startTime.get.withDurationAdded(duration, 1)
        (for {
          _ <- userDAO.insertOrUpdate(user.copy(action = ActionStates.notes.toString, endTime = Some(endTime),
            message = Some(text)))
          startTime <- Future(user.startTime.get)
        } yield {
          sendJson(JsonUtil.getTextMessageWithNotWhatIWantedJson(Messages("ar.duration.match",
            TimeUtils.dayFormat(startTime), TimeUtils.timeFormat(startTime),
            TimeUtils.timeFormat(endTime), gtfp.name), Messages("ar.duration.wrong"),
            BotPayload(ActionStates.notWhatIWanted.toString, Some(ActionStates.time.toString))))
        }).recover {
          case ex: Exception =>
            log.error(s"$prefix||${ex.toString}")
            bigFail
        }
      case bunch =>
        log.info(s"$prefix||${DataLogs.durationsData.toString}")
        sendJson(JsonUtil.getTextMessageJson(Messages("ar.duration.repeat")))
    }
  }

  private def notes(user: Botuser, text: String)(implicit userId: String) = {
    (for {
      apt <- calendarTools.scheduleTime(user.startTime.get, user.endTime.get, user.eventId.get,
        Messages("ar.duration.schedule.eventname", gtfp.eventNoun,
          user.firstName.getOrElse("") + " " + user.lastName.getOrElse("")).capitalize, userId,
        text)
    } yield {
      sendJson(JsonUtil.getTextMessageJson(Messages("ar.notes.confirm", gtfp.name, gtfp.eventNoun)))
      resetToMenuStatus
    }).recover {
      case ex: Exception =>
        log.error(s"$prefix||${ex.toString}")
        bigFail
    }
  }

  private def menu(implicit userId: String) = {
    sendJson(JsonUtil.getMenuJson(Messages("greeting", gtfp.name, Messages("brand")), Messages("schedule"),
      Messages("cancel"), Messages("view")))
  }

  private def cancel(implicit userId: String) = {
    userDAO.insertOrUpdate(Botuser(userId, ActionStates.cancelDateTime.toString)) onComplete {
      case Success(notta) =>
        sendJson(JsonUtil.getTextMessageJson(Messages("ar.cancel.ask")))
      case Failure(ex) =>
        bigFail
    }
  }

  private def cancelDateTime(text: String)(implicit userId: String) = {
    val dateTime = masterTime.getTimes(text)
    dateTime.length match {
      case 1 =>
        calendarTools.getFutureCalendarAppointments(userId) onComplete {
          case Success(suc) =>
            suc.find(_.times.start.isEqual(dateTime.head)) match {
              case Some(entry) =>
                calendarTools.cancelAppointment(entry) onComplete {
                  case Success(v) =>
                    calendarTools.patchAvailability(entry.times) onFailure {
                      case ex => log.error(s"$prefix||Failed to patch availability||${ex.getMessage}")
                    }
                    sendJson(JsonUtil.getTextMessageJson(Messages("ar.cancel.match")))
                    resetToMenuStatus
                  case Failure(f) =>
                    log.error(s"$prefix||Failed to cancel appointment||$entry||${f.getMessage}")
                    bigFail
                }
              case None =>
                sendJson(JsonUtil.getTextMessageJson(Messages("ar.cancel.noappt",
                  TimeUtils.dayFormat(dateTime.head), TimeUtils.timeFormat(dateTime.head))))
            }
          case Failure(ex) =>
            log.error(s"$prefix||Failed to get appointments from Google||${ex.getMessage}")
            bigFail
        }
      case bunch =>
        log.debug(s"$prefix||Parsed too many (or no) times for cancellation.")
        sendJson(JsonUtil.getTextMessageJson(Messages("ar.cancel.repeat")))
    }
  }

  private def view(implicit userId: String) = {
    calendarTools.getFutureCalendarAppointments(userId) onComplete {
      case Success(details) =>
        val timeStrings = getDayTimeStrings(details.map(_.times))
        if (timeStrings.isEmpty) {
          sendJson(JsonUtil.getTextMessageJson(Messages("ar.view.none", gtfp.eventNoun)))
        } else {
          sendJson(JsonUtil.getTextMessageJson(Messages("ar.view.match", gtfp.eventNoun, timeStrings)))
        }
        resetToMenuStatus
      case Failure(ex) =>
        log.error(s"Failed to get appointments from Google for user $userId, message ${
          ex.getMessage
        }")
        bigFail
    }
  }

  private def default(implicit userId: String) = {
    resetToMenuStatus
  }

  private def notWhatIWanted(returnToAction: String, user: Botuser)(implicit userId: String) = {
    log.info(s"$prefix||${DataLogs.notWhatIWantedData.toString}||$returnToAction||${user.message.getOrElse("")}")
    ActionStates.withName(returnToAction) match {
      case ActionStates.schedule =>
        sendJson(JsonUtil.getTextMessageJson(Messages("ar.notWhatIWanted.sorry", Messages("ar.day"))))
        userDAO.insertOrUpdate(user.copy(action = ActionStates.day.toString))
      case ActionStates.day =>
        sendJson(JsonUtil.getTextMessageJson(Messages("ar.notWhatIWanted.sorry", Messages("ar.time"))))
        userDAO.insertOrUpdate(user.copy(action = ActionStates.time.toString))
      case ActionStates.time =>
        sendJson(JsonUtil.getTextMessageJson(Messages("ar.notWhatIWanted.sorry", Messages("ar.duration",
          gtfp.eventNoun))))
        userDAO.insertOrUpdate(user.copy(action = ActionStates.duration.toString))
    }
  }

}
