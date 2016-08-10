package nlp

import org.joda.time.{DateTime, Duration}

trait DateTimeParser {

  def getTimes(text: String): Seq[DateTime]

  def getDates(text: String): Seq[DateTime]

  def getDatetimes(text: String): Seq[DateTime]

  def getDurations(text: String): Seq[Duration]

}
