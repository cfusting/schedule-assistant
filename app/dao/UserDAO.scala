package dao

import java.sql.Timestamp
import javax.inject.Inject

import models.{Availability, User}
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

class UserDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider,
                        availabilityDAO: AvailabilityDAO)
  extends HasDatabaseConfigProvider[JdbcProfile]{
  import pgslick.MyPostgresDriver.api._

  private val Users = TableQuery[UsersTable]

  def all(): Future[Seq[User]] = {
    Logger.info("Querying all from users table.")
    db.run(Users.result)
  }

  def iouUser(user: User) = Users insertOrUpdate user

  def insertOrUpdate(user: User): Future[Unit] = {
    Logger.info("Inserting into users table " + user.toString)
    db.run(iouUser(user)).map { _ => () }
  }

  def persistAvailability(user: User, avails: Seq[Availability]):
    Future[Unit] = {
    val ops = iouUser(user) andThen availabilityDAO.iAvailabilities(avails)
    Logger.info("Inserting availability info for user " + user.id)
    db.run(ops.transactionally).map { _ => ()}
  }

  def getUser(id: String): Future[Option[User]] = {
    Logger.info("Getting info for user: " + id)
    db.run(Users.filter(_.id === id).result.headOption)
  }

  private class UsersTable(tag: Tag) extends Table[User](tag, "users") {

    def id = column[String]("id", O.PrimaryKey)
    def action = column[String]("action")
    def timestamp = column[Option[Timestamp]]("scheduled")

    def * = (id, action, timestamp) <> (User.tupled, User.unapply)
  }
}
