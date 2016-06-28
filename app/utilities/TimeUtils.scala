package utilities

import java.sql.Timestamp

import org.joda.time.{DateTime, Period}
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
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

  def validateDay(dt: DateTime): DateTime = {
    if (dt.isBefore(new DateTime)) {
      dt.minusWeeks(1)
    } else {
      dt
    }
  }

}
