package nlp

import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.pipeline._
import edu.stanford.nlp.time._
import org.joda.time.{DateTime, Period}
import java.util.Properties

import edu.stanford.nlp.util.CoreMap

import scala.collection.JavaConversions._
import org.joda.time.format.ISODateTimeFormat
import play.api.Logger
import utilities.TimeUtils

class MasterTime {

  val log = Logger(this.getClass)

  val duration = "DURATION"
  val time = "Temporal"

  val props = new Properties
  props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref")
  val pipe = new StanfordCoreNLP(props)
  val timeAnnotator = new TimeAnnotator
  pipe.addAnnotator(timeAnnotator)
  val fmt = ISODateTimeFormat.dateTime

  private def parse(text: String): Seq[CoreMap] = {
    val ano = new Annotation(text)
    val theDate = new DateTime
    ano.set(classOf[CoreAnnotations.DocDateAnnotation], fmt.print(theDate))
    pipe.annotate(ano)
    ano.get(classOf[TimeAnnotations.TimexAnnotations])
  }

  def getDateTimes(text: String): Seq[DateTime] = {
    parse(text).filter(timexAnn => {
      val timeExpr: TimeExpression = timexAnn.get(classOf[TimeExpression.Annotation])
      timeExpr.getValue.getType == time
    }).map(timexAnn => {
      val timeExpr: TimeExpression = timexAnn.get(classOf[TimeExpression.Annotation])
      val temporal = timeExpr.getTemporal.getTimexValue
      val nextTime = new DateTime(temporal)
      log.debug("Parsed datetime: " + fmt.print(nextTime))
      nextTime
    })
  }

  def getPeriods(text: String): Seq[Period] = {
    parse(text).filter(timexAnn => {
      val timeExpr: TimeExpression = timexAnn.get(classOf[TimeExpression.Annotation])
      timeExpr.getValue.getType == duration
    }).map(timexAnn => {
      val timeExpr: TimeExpression = timexAnn.get(classOf[TimeExpression.Annotation])
      val period= timeExpr.getTemporal.getDuration.getJodaTimePeriod
      log.debug("Parsed period: " + TimeUtils.getHourMinutePeriodFormatter.print(period))
      period
    })
  }
}
