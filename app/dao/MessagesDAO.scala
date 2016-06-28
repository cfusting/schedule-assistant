package dao

import javax.inject.Inject

import models.SimpleMessage
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

class MessagesDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  import pgslick.MyPostgresDriver.api._

  private val MessagesQ = TableQuery[MessagesTable]

  def all(): Future[Seq[SimpleMessage]] = {
    Logger.info("Querying all from users table.")
    db.run(MessagesQ.result)
  }

  def insertOrUpdate(mes: SimpleMessage): Future[Unit] = {
    Logger.info("Inserting into messages table " + mes.toString)
    db.run(MessagesQ.insertOrUpdate(mes)).map { _ => () }
  }

  def getMessages(id: String): Future[Option[SimpleMessage]] = {
    Logger.info("Checking for message: " + id)
    db.run(MessagesQ.filter(_.id === id).result.headOption)
  }

  private class MessagesTable(tag: Tag) extends Table[SimpleMessage](tag, "messages") {

    def id = column[String]("id", O.PrimaryKey)
    def seq = column[Int]("seq")

    def * = (id, seq) <> (SimpleMessage.tupled, SimpleMessage.unapply)
  }

}

