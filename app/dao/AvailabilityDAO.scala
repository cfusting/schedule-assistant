package dao

import java.sql.Timestamp
import javax.inject.Inject

import models.Availability
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

class AvailabilityDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile]{
  import pgslick.MyPostgresDriver.api._

  private val Avails = TableQuery[AvailabilityTable]

  def dAvailability(id: String) = Avails.filter(_.userId === id).delete

  def deleteAvailability(id: String): Future[Unit] = {
    Logger.info("Deleting availabilities for user: " + id)
    db.run(dAvailability(id)).map { _ => ()}
  }

  def iAvailability(avail: Availability) = Avails insertOrUpdate avail

  def insertAvailability(avail: Availability): Future[Unit] = {
    Logger.info("Inserting into availability table " + avail.toString)
    db.run(iAvailability(avail)).map{ _ => ()}
  }

  def getAvailability(id: String): Future[Seq[Availability]] = {
    Logger.info("Getting availabilities for user: " + id)
    val op = Avails.filter(_.userId === id)
    db.run(op.result)
  }

  private class AvailabilityTable(tag: Tag) extends Table[Availability](tag, "availability") {

    def userId = column[String]("user_id")
    def eventId = column[String]("event_id")
    def startTime = column[Timestamp]("start_time")
    def endTime = column[Timestamp]("end_time")
    def userTime = column[Timestamp]("user_time")
    def * = (userId, eventId, startTime, endTime, userTime) <> (Availability.tupled, Availability.unapply)
    def pk = primaryKey("user_event", (userId, eventId))
  }
}
