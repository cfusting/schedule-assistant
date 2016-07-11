package nlp

import org.joda.time.{DateTime, Duration}

trait DateTimeParser {

  def getDateTimes(text: String): Seq[DateTime]

  def getDurations(text: String): Seq[Duration]

}
