package models.daos

import java.sql.Timestamp
import javax.inject.Inject

import models.Botuser
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

class BotuserDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile]{
  import pgslick.MyPostgresDriver.api._

  val log = Logger(this.getClass)

  private val Botusers = TableQuery[BotusersTable]

  def all(): Future[Seq[Botuser]] = {
    db.run(Botusers.result)
  }

  def iouUser(user: Botuser) = Botusers insertOrUpdate user

  def insertOrUpdate(user: Botuser): Future[Unit] = {
    db.run(iouUser(user)).map { _ => () }
  }

  def updateAction(action: String)(implicit userId: String) = {
    val query = Botusers.filter(u => u.id === userId).map(_.action).update(action)
    db.run(query)
  }

  def updateName(firstName: String, lastName: String)(implicit userId: String) = {
    val query = for { c <- Botusers if c.id === userId} yield (c.firstName, c.lastName)
    db.run(query.update(Some(firstName), Some(lastName)))
  }

  def getUser(id: String): Future[Option[Botuser]] = {
    db.run(Botusers.filter(_.id === id).result.headOption)
  }

  private class BotusersTable(tag: Tag) extends Table[Botuser](tag, "bot_users") {

    def id = column[String]("id", O.PrimaryKey)
    def action = column[String]("action")
    def startTime = column[Option[Timestamp]]("starttime")
    def eventId = column[Option[String]]("eventid")
    def firstName = column[Option[String]]("firstname")
    def lastName = column[Option[String]]("lastname")
    def message = column[Option[String]]("message")
    def endTime = column[Option[Timestamp]]("endtime")

    def * = (id, action, startTime, eventId, firstName, lastName, message, endTime) <> (Botuser.tupled, Botuser.unapply)
  }
}
