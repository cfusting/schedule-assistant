package google

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.calendar.model.Event.ExtendedProperties
import com.google.api.services.calendar.model._
import models.{Appointment, Availability, TimeRange}
import org.joda.time.{DateTime, Duration}
import play.api.{Configuration, Logger}
import utilities.TimeUtils
import utilities.TimeUtils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class CalendarTools(conf: Configuration, accessToken: String, refreshToken: String, calendar: String) {

  val log = Logger(this.getClass)
  val availabilityCode = "0"
  val userIdKey = "userId"
  val available = "Available"
  val jsonFactory = new JacksonFactory()
  val httpTransport = new NetHttpTransport()

  val credential = new GoogleCredential.Builder()
    .setJsonFactory(jsonFactory)
    .setTransport(httpTransport)
    .setClientSecrets(conf.underlying.getString("silhouette.google.clientID"),
      conf.underlying.getString("silhouette" + ".google" + ".clientSecret"))
    .build()
    .setAccessToken(accessToken)
    .setRefreshToken(refreshToken)
  val service = new com.google.api.services.calendar.Calendar.Builder(
    httpTransport, jsonFactory, credential
  ).setApplicationName("Scheduler")
    .build()

  def getAvailabilityForDay(day: DateTime): Future[Seq[TimeRange]] = {
    Future {
      getAvailableEventsForDay(day).map { event =>
        TimeRange(event.getStart.getDateTime, event.getEnd.getDateTime)
      }
    }
  }

  private def getAvailableEventsForDay(day: DateTime): Seq[Event] = {
    getEventsForDay(day).getItems.filter((x: Event) =>
      x.getSummary == available).seq
  }

  private def getEventsForDay(day: DateTime): Events = {
    val range = googleStartEnd(day)
    service.events.list(calendar)
      .setTimeMin(range.start)
      .setTimeMax(range.end)
      .setOrderBy("startTime")
      .setSingleEvents(true)
      .execute
  }

  def googleStartEnd(day: DateTime): TimeRange = {
    val dayEnd = day.plusDays(1).withTimeAtStartOfDay()
    TimeRange(day, dayEnd)
  }

  def matchAvailabilityForTime(times: Seq[DateTime])(implicit userId: String): Seq[Availability] = {
    val events = getAvailableEventsForDay(times.head.withTimeAtStartOfDay)
    times.map(time =>
      events.filter(isTimeInWindow(time, _))
        .map(x => Availability(userId, x.getId, x.getStart.getDateTime, x.getEnd.getDateTime, time))
    ).flatten
  }

  private def isTimeInWindow(time: DateTime, event: Event): Boolean = {
    (time.isEqual(event.getStart.getDateTime) || time.isAfter(event.getStart.getDateTime)) &&
      (time.isEqual(event.getEnd.getDateTime) || time.isBefore(event.getEnd.getDateTime))
  }

  def matchDuration(startTime: DateTime, endTime: DateTime, eventId: String)
                   (implicit userId: String): Future[DateTime] = {
    Future[DateTime] {
      val event = service.events.get(calendar, eventId).execute
      if (isTimeInWindow(startTime, event) && isTimeInWindow(endTime, event)) {
        endTime
      } else {
        throw new CalendarException("Event time not available.")
      }
    }
  }

    /**
      * Schedule an appointment.
      *
      * Ensures the appointment falls within the availability window.
      */
    def scheduleTime(appointmentStartTime: DateTime, appointmentEndTime: DateTime, eventId: String, eventName: String,
                     userId: String, notes: String): Future[Appointment] = {
      Future {
        val event = service.events.get(calendar, eventId).execute
        if (isTimeInWindow(appointmentStartTime, event) && isTimeInWindow(appointmentEndTime, event)) {
          service.events.delete(calendar, eventId).execute
          val events = partitionAvailability(event, appointmentStartTime, appointmentEndTime, eventName, userId, notes)
          val apt = events map { x =>
            service.events.insert(calendar, x).execute
          }
          val aptId = apt.filter(_.getSummary != available).head.getId
          Appointment(aptId, TimeRange(appointmentStartTime, appointmentEndTime))
        } else {
          throw new CalendarException("Time is no longer available")
        }
      }
    }

    /**
      * Partition an availability into one, two, or three parts depending on the time of the appointment.
      *
      * @param availability
      * @param appointmentStartTime
      * @param appointmentEndTime
      * @param eventName
      * @return
      */
    private def partitionAvailability(availability: Event, appointmentStartTime: DateTime,
                                      appointmentEndTime: DateTime, eventName: String, userId: String,
                                      notes: String): Seq[Event] =
    {
      var events = List[Event]()
      val appointment = new Event()
        .setSummary(eventName)
        .setStart(new EventDateTime().setDateTime(appointmentStartTime))
        .setEnd(new EventDateTime().setDateTime(appointmentEndTime))
        .setDescription(notes)
        .setExtendedProperties(new ExtendedProperties().setShared(Map(userIdKey -> userId)))
      events = appointment :: events
      if (!appointmentStartTime.isEqual(availability.getStart.getDateTime)) {
        val availabilityBefore = new Event().setSummary(available)
          .setDescription("Generated when breaking apart availability")
          .setStart(new EventDateTime().setDateTime(availability.getStart.getDateTime))
          .setEnd(new EventDateTime().setDateTime(appointmentStartTime))
          .setExtendedProperties(new ExtendedProperties().setShared(Map(userIdKey -> availabilityCode)))
        events = availabilityBefore :: events
      }
      if (!appointmentEndTime.isEqual(availability.getEnd.getDateTime)) {
        val availabilityAfter = new Event().setSummary(available)
          .setDescription("Generated when breaking apart availability")
          .setStart(new EventDateTime().setDateTime(appointmentEndTime))
          .setEnd(new EventDateTime().setDateTime(availability.getEnd.getDateTime))
          .setExtendedProperties(new ExtendedProperties().setShared(Map(userIdKey -> availabilityCode)))
        events = availabilityAfter :: events
      }
      events
    }

    def updateEvent(eventId: String, notes: String): Future[Unit] = {
      Future {
        log.debug(s"Updating event $eventId with notes $notes")
        val event = new Event().setDescription(notes)
        service.events.patch(calendar, eventId, event).execute
      }
    }

    def getFutureCalendarAppointments(userId: String): Future[Seq[Appointment]] = {
      Future[Seq[Appointment]] {
        val now = new DateTime()
        val events = service.events.list(calendar)
          .setTimeMin(now)
          .setOrderBy("startTime")
          .setSingleEvents(true)
          .execute
          .getItems
        events.filter {
          x =>
            Try(x.getExtendedProperties.getShared.get(userIdKey)) match {
              case Success(key) => key == userId
              case Failure(ex) => false
            }
        }.map {
          event =>
            Appointment(event.getId, TimeRange(event.getStart.getDateTime, event.getEnd.getDateTime))
        }
      }
    }

    def cancelAppointment(apt: Appointment)(implicit userId: String): Future[Unit] = {
      Future[Unit] {
        service.events.delete(calendar, apt.eventId).execute
      }
    }

    def patchAvailability(timeRange: TimeRange)(implicit userId: String): Future[Unit] = {
      Future {
        val events = service.events.list(calendar)
          .setTimeMax(timeRange.end.plusSeconds(1))
          .setTimeMin(timeRange.start.minusSeconds(1))
          .setOrderBy("startTime")
          .setSingleEvents(true)
          .execute
          .getItems
          .filter(_.getSummary == available)
        val newAvailability = new Event().setSummary(available)
          .setDescription("Automatically generated when patching availability.")
        events foreach {
          event =>
            if (TimeUtils.googleDateTime2dateTime(event.getEnd.getDateTime).equals(timeRange.start)) {
              newAvailability.setStart(event.getStart)
              service.events.delete(calendar, event.getId).execute
            }
            if (TimeUtils.googleDateTime2dateTime(event.getStart.getDateTime).equals(timeRange.end)) {
              newAvailability.setEnd(event.getEnd)
              service.events.delete(calendar, event.getId).execute
            }
        }
        if (newAvailability.getStart == null) newAvailability.setStart(new EventDateTime().setDateTime(timeRange.start))
        if (newAvailability.getEnd == null) newAvailability.setEnd(new EventDateTime().setDateTime(timeRange.end))
        service.events.insert(calendar, newAvailability).execute
      }
    }

    def getCalendarList: Future[Seq[(String, String)]] = {
      Future {
        service.calendarList.list.execute().getItems.filter(_.getAccessRole == "owner").map {
          x => (x.getId, x.getSummary)
        }
      }
    }
  }

  class CalendarException(message: String = null, cause: Throwable = null)
    extends RuntimeException(CalendarException.defaultMessage(message, cause), cause)

  object CalendarException {
    def defaultMessage(message: String, cause: Throwable) =
      if (message != null) message
      else if (cause != null) cause.toString
      else null
  }
