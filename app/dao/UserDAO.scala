package dao

import javax.inject.Inject

import models.User
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

class UserDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile]{
  import driver.api._

  private val Users = TableQuery[UsersTable]

  def all(): Future[Seq[User]] = {
    Logger.info("Querying all from users table")
    db.run(Users.result)
  }

  def insertOrUpdate(user: User): Future[Unit] = {
    Logger.info("Inserting into users table" + user.toString)
    db.run(Users insertOrUpdate user).map { _ => () }
  }

  def getUser(id: String): Future[Option[User]] = {
    Logger.info("Getting info for user: " + id)
    db.run(Users.filter(_.id === id).result.headOption)
  }

  private class UsersTable(tag: Tag) extends Table[User](tag, "USERS") {

    def id = column[String]("ID", O.PrimaryKey)
    def action = column[String]("ACTION")

    def * = (id, action) <> (User.tupled, User.unapply _)
  }
}
