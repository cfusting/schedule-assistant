package utilities

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object TimeUtils {
  def dayFormat(dt: DateTime) = DateTimeFormat.forPattern("EEEEE MMMM d").print(dt)

  def timeFormat(dt: DateTime) = DateTimeFormat.forPattern("h a").print(dt)

  def isoFormat(dt: DateTime) = DateTimeFormat.fullDate.print(dt)
}
