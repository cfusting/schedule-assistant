package utilities

import java.sql.Timestamp

import org.joda.time.{DateTime, Duration, Period}
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat, PeriodFormatter, PeriodFormatterBuilder}
import com.github.tminglei.slickpg.Range
import models.{Availability, TimeRange}
import play.Logger

object TimeUtils {

  implicit def dateTime2Timestamp(dt: DateTime): Timestamp = {
    new Timestamp(dt.getMillis)
  }

  implicit def Timestamp2DateTime(ts: Timestamp): DateTime = {
    new DateTime(ts.getTime)
  }

  implicit def googleDateTime2dateTime(dt: com.google.api.client.util.DateTime): DateTime = {
    new DateTime(dt.getValue)
  }

  implicit def googleDateTime2Timestamp(dt: com.google.api.client.util.DateTime): Timestamp = {
    new Timestamp(dt.getValue)
  }

  implicit def dateTime2googleTime(dt: DateTime): com.google.api.client.util.DateTime = {
    new com.google.api.client.util.DateTime(dt.getMillis)
  }

  val tsFormatter = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  def dayFormat(dt: DateTime) = DateTimeFormat.forPattern("EEEEE MMMM d").print(dt)

  def timeFormat(dt: DateTime) = DateTimeFormat.forPattern("h:mm a").print(dt)

  def isoFormat(dt: DateTime) = ISODateTimeFormat.dateTime.print(dt)

  def niceFormat(dt: DateTime) = DateTimeFormat.forPattern("h:mm a EEEEE MMMM d").print(dt)

  def ts(str: String) = new Timestamp(tsFormatter.parse(str).getTime)

  def matchTime(dt: DateTime, range: Seq[TimeRange]): Option[DateTime] = {
    Logger.info("Requested datetime: " + isoFormat(dt))
    range.find { x =>
      Logger.info("Available start datetime: " + isoFormat(x.start))
      x.start == dt
    }.map(_.start)
  }

  def dateTimeForDayAndTime(day: DateTime, time: DateTime): DateTime = {
    day.withTime(time.toLocalTime)
  }

  /**
    * Transforms the datetime such that it represents the start of the day in the future.
    *
    * @param dt
    * @return
    */
  def getFutureStartOfDay(dt: DateTime): DateTime = {
    val day = dt.withTimeAtStartOfDay
    if (day.isBefore(new DateTime)) {
      day.minusWeeks(1)
    } else {
      day
    }
  }

  def getHourMinutePeriodFormatter = {
    new PeriodFormatterBuilder()
      .appendHours
      .appendSuffix(" hour", " hours")
      .appendSeparator(" and ")
      .appendMinutes
      .appendSuffix(" minute", " minutes")
      .toFormatter
  }

  def getHourMinutePeriodString(dt: DateTime, dt2: DateTime): String = {
    getHourMinutePeriodFormatter.print(new Period(dt, dt2))
  }

}
