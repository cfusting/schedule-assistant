package modules

import javax.inject.Singleton

import ErrorHandlers.CustomSecuredErrorHandler
import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.actions.SecuredErrorHandler
import net.codingwell.scalaguice.ScalaModule
import nlp.{DateTimeParser, MasterTime}

class BotModule extends AbstractModule with ScalaModule {

  override def configure() = {
    bind[DateTimeParser].to[MasterTime].in(classOf[Singleton])
    bind[SecuredErrorHandler].to[CustomSecuredErrorHandler]
  }

}
