package modules

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import nlp.{DateTimeParser, MasterTime}

class BotModule extends AbstractModule with ScalaModule {

  override def configure() = {
    bind[DateTimeParser].to[MasterTime].asEagerSingleton()
  }

}
