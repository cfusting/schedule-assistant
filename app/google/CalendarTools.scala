package google

import javax.inject.Inject

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.calendar.model._
import models.TimeRange
import org.joda.time.DateTime
import play.api.{Configuration, Logger}
import utilities.TimeUtils

import scala.collection.JavaConversions._

class CalendarTools @Inject()(conf: Configuration) {

  implicit def googleDateTime2dateTime(dt: com.google.api.client.util.DateTime): DateTime = {
    new DateTime(dt.getValue)
  }

  implicit def dateTime2googleTime(dt: DateTime): com.google.api.client.util.DateTime = {
    new com.google.api.client.util.DateTime(dt.getMillis)
  }

  val jsonFactory = new JacksonFactory()
  val httpTransport = new NetHttpTransport()
  val credential = new GoogleCredential.Builder()
    .setJsonFactory(jsonFactory)
    .setTransport(httpTransport)
    .setClientSecrets(conf.underlying.getString("client.id"), conf.underlying.getString("client.secret"))
    .build()
    .setAccessToken(conf.underlying.getString("client.access.token"))
  //    .setRefreshToken(conf.underlying.getString("client.refresh.token"))
  val service = new com.google.api.services.calendar.Calendar.Builder(
    httpTransport, jsonFactory, credential
  ).setApplicationName("Scheduler")
    .build()

  def getAvailabilityForDay(dt: DateTime): Seq[TimeRange] = {
    getAvailableEvents(dt).map { event =>
      TimeRange(event.getStart.getDateTime, event.getEnd.getDateTime)
    }
  }

  private def getAvailableEvents(dt: DateTime): Seq[Event] = {
    getEventsForStartTime(dt).getItems.filter((x: Event) =>
    x.getSummary == "Available").seq
  }

  private def getEventsForStartTime(dt: DateTime): Events = {
    val range = googleStartEnd(dt)
    service.events.list("primary")
      .setTimeMin(range.start)
      .setTimeMax(range.end)
      .setOrderBy("startTime")
      .setSingleEvents(true)
      .execute()
  }

  def googleStartEnd(dt: DateTime): TimeRange = {
    val start = dt.withTimeAtStartOfDay
    val end = dt.plusDays(1).withTimeAtStartOfDay()
    TimeRange(start, end)
  }

  def postAppt(dt: DateTime, userName: String) = {
    deleteEvents(dt)
    insertEvent(dt, userName)
  }

  private def deleteEvents(dt: DateTime) = {
    getAvailableEvents(dt).find(x => googleDateTime2dateTime(x.getStart.getDateTime) == dt)
      .foreach{x =>
        service.events.delete("primary", x.getId).execute
        Logger.info("Deleted availability event at " + TimeUtils.isoFormat(dt))
      }
  }

  private def insertEvent(dt: DateTime, userName: String) = {
    val event = new Event()
      .setSummary("Lesson with " + userName)
      .setDescription("Scheduled at " + TimeUtils.niceFormat(new DateTime))
      .setStart(new EventDateTime().setDateTime(dt))
      .setEnd(new EventDateTime().setDateTime(dt.plusHours(1)))
    service.events.insert("primary", event).execute
    Logger.info("Created event with " + userName + " at " + TimeUtils.isoFormat(dt))
  }

}
