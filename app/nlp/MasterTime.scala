package nlp

import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.pipeline._
import edu.stanford.nlp.time._
import org.joda.time.DateTime
import java.util.Properties

import scala.collection.JavaConversions._
import org.joda.time.format.ISODateTimeFormat
import play.api.Logger

class MasterTime {

  val props = new Properties
  props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref")
  val pipe = new StanfordCoreNLP(props)
  val timeAnnotator = new TimeAnnotator
  pipe.addAnnotator(timeAnnotator)

  def parseDateTime(text: String): Seq[DateTime] = {
    val ano = new Annotation(text)
    val theDate = new DateTime
    val fmt = ISODateTimeFormat.dateTime
    ano.set(classOf[CoreAnnotations.DocDateAnnotation], fmt.print(theDate))
    pipe.annotate(ano)
    Logger.info("Parsing date and time info from the text: " + ano.get(classOf[CoreAnnotations.TextAnnotation]))
    val timexAnsAll = ano.get(classOf[TimeAnnotations.TimexAnnotations])
    timexAnsAll.map(timexAnn => {
      val timeExpr = timexAnn.get(classOf[TimeExpression.Annotation])
      val temporal = timeExpr.getTemporal.getTimexValue
      val nextTime = new DateTime(temporal)
      Logger.info("Parsed datetime: " + fmt.print(nextTime))
      nextTime
    })
  }
}
