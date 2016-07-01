package google

import javax.inject.Inject

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.calendar.model._
import models.{Appointment, Availability, TimeRange}
import org.joda.time.{DateTime, Period}
import play.api.{Configuration, Logger}
import utilities.TimeUtils
import utilities.TimeUtils._

import scala.collection.JavaConversions._

class CalendarTools @Inject()(conf: Configuration) {
  
  val log = Logger(this.getClass)

  val jsonFactory = new JacksonFactory()
  val httpTransport = new NetHttpTransport()
  val credential = new GoogleCredential.Builder()
    .setJsonFactory(jsonFactory)
    .setTransport(httpTransport)
    .setClientSecrets(conf.underlying.getString("client.id"), conf.underlying.getString("client.secret"))
    .build()
    .setAccessToken(conf.underlying.getString("client.access.token"))
  val service = new com.google.api.services.calendar.Calendar.Builder(
    httpTransport, jsonFactory, credential
  ).setApplicationName("Scheduler")
    .build()

  def getAvailabilityForDay(day: DateTime): Seq[TimeRange] = {
    getAvailableEventsForDay(day).map { event =>
      TimeRange(event.getStart.getDateTime, event.getEnd.getDateTime)
    }
  }

  private def getAvailableEventsForDay(day: DateTime): Seq[Event] = {
    getEventsForDay(day).getItems.filter((x: Event) =>
      x.getSummary == "Available").seq
  }

  private def getEventsForDay(day: DateTime): Events = {
    val range = googleStartEnd(day)
    service.events.list("primary")
      .setTimeMin(range.start)
      .setTimeMax(range.end)
      .setOrderBy("startTime")
      .setSingleEvents(true)
      .execute()
  }

  def googleStartEnd(day: DateTime): TimeRange = {
    val dayEnd = day.plusDays(1).withTimeAtStartOfDay()
    TimeRange(day, dayEnd)
  }

  def postAppt(dt: DateTime, userName: String) = {
    deleteEvents(dt)
    insertAppointment(dt, userName)
  }

  private def deleteEvents(dt: DateTime) = {
    getAvailableEventsForDay(dt).find(x => googleDateTime2dateTime(x.getStart.getDateTime) == dt)
      .foreach { x =>
        service.events.delete("primary", x.getId).execute
        log.debug("Deleted availability event at " + TimeUtils.isoFormat(dt))
      }
  }

  private def insertAppointment(dt: DateTime, userName: String) = {
    val event = new Event()
      .setSummary("Lesson with " + userName)
      .setDescription("Scheduled at " + TimeUtils.niceFormat(new DateTime))
      .setStart(new EventDateTime().setDateTime(dt))
      .setEnd(new EventDateTime().setDateTime(dt.plusHours(1)))
    service.events.insert("primary", event).execute
    log.debug("Created event with " + userName + " at " + TimeUtils.isoFormat(dt))
  }

  def matchAvailabilityForTime(time: DateTime)(implicit userId: String): Seq[Availability] = {
    getAvailableEventsForDay(time.withTimeAtStartOfDay)
      .filter(isTimeInWindow(time, _))
      .map(x => Availability(userId, x.getId, x.getStart.getDateTime, x.getEnd.getDateTime, time))
  }

  private def isTimeInWindow(time: DateTime, event: Event): Boolean = {
    (time.isEqual(event.getStart.getDateTime) || time.isAfter(event.getStart.getDateTime)) &&
      (time.isEqual(event.getEnd.getDateTime) || time.isBefore(event.getEnd.getDateTime))
  }

  /**
    * Schedule an appointment.
    *
    * Ensures the appointment falls within the availability window.
    *
    * @param appointmentStartTime
    * @param duration
    * @param eventId
    * @param userName
    * @return
    */
  def scheduleTime(appointmentStartTime: DateTime, duration: Period, eventId: String, userName: String):
  Option[Appointment] = {
    try {
      val appointmentEndTime = appointmentStartTime.withDurationAdded(duration.toStandardDuration.getMillis, 1)
      val event = service.events.get("primary", eventId).execute
      if (isTimeInWindow(appointmentStartTime, event) && isTimeInWindow(appointmentEndTime, event)) {
        service.events.delete("primary", eventId).execute
        val events = partitionAvailability(event, appointmentStartTime, appointmentEndTime, duration, userName)
        val apt = events map { x =>
          service.events.insert("primary", x).execute
        }
        val aptId = apt.filter(_.getSummary != "Available").head.getId
        Some(Appointment(aptId, TimeRange(appointmentStartTime, appointmentEndTime)))
      } else {
        None
      }
    } catch {
      case e: Exception =>
        log.error("Failed to schedule appointment for user: " + userName + " message " + e.getMessage)
        None
    }
  }

  /**
    * Partition an availability into one, two, or three parts depending on the time of the appointment.
    *
    * @param availability
    * @param appointmentStartTime
    * @param appointmentEndTime
    * @param duration
    * @param userName
    * @return
    */
  private def partitionAvailability(availability: Event, appointmentStartTime: DateTime,
                                    appointmentEndTime: DateTime, duration: Period,
                                    userName: String): Seq[Event] = {
    var events = List[Event]()
    val appointment = new Event().setSummary("Lesson with " + userName)
      .setStart(new EventDateTime().setDateTime(appointmentStartTime))
      .setEnd(new EventDateTime().setDateTime(appointmentEndTime))
    events = appointment :: events
    if (!appointmentStartTime.isEqual(availability.getStart.getDateTime)) {
      val availabilityBefore = new Event().setSummary("Available")
        .setDescription("Generated when breaking apart availability")
        .setStart(new EventDateTime().setDateTime(availability.getStart.getDateTime))
        .setEnd(new EventDateTime().setDateTime(appointmentStartTime))
      events = availabilityBefore :: events
    }
    if (!appointmentEndTime.isEqual(availability.getEnd.getDateTime)) {
      val availabilityAfter = new Event().setSummary("Available")
        .setDescription("Generated when breaking apart availability")
        .setStart(new EventDateTime().setDateTime(appointmentEndTime))
        .setEnd(new EventDateTime().setDateTime(availability.getEnd.getDateTime))
      events = availabilityAfter :: events
    }
    events
  }

  def updateEvent(eventId: String, notes: String) = {
    log.debug(s"Updating event $eventId with notes $notes")
    val event = new Event().setDescription(notes)
    service.events.patch("primary", eventId, event).execute
  }
}
