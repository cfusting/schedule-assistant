package nlp

import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.pipeline._
import edu.stanford.nlp.time._
import org.joda.time.{DateTime, Duration}
import java.util.Properties

import com.google.inject.Singleton
import edu.stanford.nlp.time.SUTime.TimexType
import edu.stanford.nlp.util.CoreMap

import scala.collection.JavaConversions._
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import play.api.Logger
import utilities.TimeUtils

@Singleton
class MasterTime extends DateTimeParser {

  val log = Logger(this.getClass)

  val props = new Properties
  props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref")
  val pipe = new StanfordCoreNLP(props)
  val timeAnnotator = new TimeAnnotator
  pipe.addAnnotator(timeAnnotator)
  val fmt = DateTimeFormat.forPattern("yyyy-MM-dd")

  private def parse(text: String): Seq[CoreMap] = {
    val ano = new Annotation(text)
    val theDate = new DateTime
    ano.set(classOf[CoreAnnotations.DocDateAnnotation], fmt.print(theDate))
    pipe.annotate(ano)
    ano.get(classOf[TimeAnnotations.TimexAnnotations])
  }

  private def getDateTimes(text: String, timeType: TimexType) = {
    parse(text).filter(timexAnn => {
      timexAnn.get(classOf[TimeExpression.Annotation]).getTemporal.getTimexType == timeType
    }).map(timexAnn => {
      val dateTime = new DateTime(timexAnn.get(classOf[TimeExpression.Annotation]).getTemporal.getTimexValue)
      log.debug(s"Parsed: ${fmt.print(dateTime)} from: $text")
      dateTime
    })
  }

  def getDatetimes(text: String) = {
    parse(text).filter(timexAnn => {
      timexAnn.get(classOf[TimeExpression.Annotation]).getTemporal.getTimexType == TimexType.TIME ||
      timexAnn.get(classOf[TimeExpression.Annotation]).getTemporal.getTimexType == TimexType.DATE
    }).map(timexAnn => {
      val dateTime = new DateTime(timexAnn.get(classOf[TimeExpression.Annotation]).getTemporal.getTimexValue)
      log.debug(s"Parsed: ${fmt.print(dateTime)} from: $text")
      dateTime
    })
  }

  def getTimes(text: String): Seq[DateTime] = {
    getDateTimes(text, TimexType.TIME)
  }

  def getDates(text: String): Seq[DateTime] = {
    getDateTimes(text, TimexType.DATE)
  }

  def getDurations(text: String): Seq[Duration] = {
    parse(text).filter(timexAnn => {
      timexAnn.get(classOf[TimeExpression.Annotation]).getTemporal.getTimexType == TimexType.DURATION
    }).map(timexAnn => {
      val duration = timexAnn.get(classOf[TimeExpression.Annotation]).getTemporal.getDuration.getJodaTimeDuration
      log.debug(s"Parsed: ${TimeUtils.getHourMinutePeriodFormatter.print(duration.toPeriod)} from text: $text")
      duration
    })
  }
}
