package dao

import java.sql.Timestamp
import javax.inject.Inject

import models.{Availability, User}
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

class UserDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile]{
  import pgslick.MyPostgresDriver.api._

  val log = Logger(this.getClass)

  private val Users = TableQuery[UsersTable]

  def all(): Future[Seq[User]] = {
    log.debug("Querying all from users table.")
    db.run(Users.result)
  }

  def iouUser(user: User) = Users insertOrUpdate user

  def insertOrUpdate(user: User): Future[Unit] = {
    log.debug("Inserting into users table " + user.toString)
    db.run(iouUser(user)).map { _ => () }
  }

  def updateAction(action: String)(implicit userId: String) = {
    log.debug(s"Updating user $userId")
    val query = Users.filter(u => u.id === userId).map(_.action).update(action)
    db.run(query)
  }

  def updateName(firstName: String, lastName: String)(implicit userId: String) = {
    log.debug(s"Updating user: $userId")
    val query = for { c <- Users if c.id === userId} yield (c.firstName, c.lastName)
    db.run(query.update(Some(firstName), Some(lastName)))
  }

  def getUser(id: String): Future[Option[User]] = {
    log.debug("Getting info for user: " + id)
    db.run(Users.filter(_.id === id).result.headOption)
  }

  private class UsersTable(tag: Tag) extends Table[User](tag, "users") {

    def id = column[String]("id", O.PrimaryKey)
    def action = column[String]("action")
    def timestamp = column[Option[Timestamp]]("scheduled")
    def eventId = column[Option[String]]("event_id")
    def firstName = column[Option[String]]("first_name")
    def lastName = column[Option[String]]("last_name")

    def * = (id, action, timestamp, eventId, firstName, lastName) <> (User.tupled, User.unapply)
  }
}
