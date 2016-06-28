package dao

import java.sql.Timestamp
import javax.inject.Inject

import models.Availability
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import com.github.tminglei.slickpg.Range
import scala.concurrent.Future

class AvailabilityDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile]{
  import pgslick.MyPostgresDriver.api._

  private val Avails = TableQuery[AvailabilityTable]

  def dAvailability(id: String) = Avails.filter(_.userId === id).delete

  def deleteAvailabilities(id: String): Future[Unit] = {
    Logger.info("Deleting availabilities for user: " + id)
    db.run(dAvailability(id)).map { _ => ()}
  }

  def iAvailabilities(avails: Seq[Availability]) = Avails ++= avails

  def insertAvailabilities(avails: Seq[Availability]): Future[Unit] = {
    Logger.info("Inserting into availability table " + avails.toString)
    db.run(iAvailabilities(avails)).map{ _ => ()}
  }

  def getAvailabilities(id: String): Future[Seq[Availability]] = {
    Logger.info("Getting availabilities for user: " + id)
    val op = Avails.filter(_.userId === id)
    db.run(op.result)
  }

  private class AvailabilityTable(tag: Tag) extends Table[Availability](tag, "availability") {

    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[String]("user_id")
    def period = column[Range[Timestamp]]("period")

    def * = (userId, period, id) <> (Availability.tupled, Availability.unapply)
  }
}
