package models.daos

import java.util.UUID
import javax.inject.Inject

import com.mohiva.play.silhouette.api.LoginInfo
import models.User
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

/**
  * Give access to the user object using Slick
  */
class UserDAOImpl @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends UserDAO with DAOSlick {

  import driver.api._

  /**
    * Finds a user by its login info.
    *
    * @param loginInfo The login info of the user to find.
    * @return The found user or None if no user for the given login info could be found.
    */
  def find(loginInfo: LoginInfo) = {
    val userQuery = for {
      initialDbLoginInfo <- loginInfoQuery(loginInfo)
      initialDbUserLoginInfo <- slickUserLoginInfos.filter(_.loginInfoId === initialDbLoginInfo.id)
      dbUserLoginInfo <- slickUserLoginInfos.filter(_.userID === initialDbUserLoginInfo.userID)
      dbLoginInfo <- slickLoginInfos.filter(_.id === dbUserLoginInfo.loginInfoId)
      dbUser <- slickUsers.filter(_.id === dbUserLoginInfo.userID)
    } yield (dbUser, dbLoginInfo)
    db.run(userQuery.result).map(mapUser)
  }

  private def mapUser(results: Seq[(DBUser, DBLoginInfo)]): Option[User] = {
    var loginList = List[LoginInfo]()
    results.foreach {
      case (user, loginInfo) =>
        loginList = LoginInfo(loginInfo.providerID, loginInfo.providerKey) :: loginList
    }
    results.headOption.map {
      case (user, loginInfo) =>
        User(
          UUID.fromString(user.userID),
          loginList,
          user.firstName,
          user.lastName,
          user.fullName,
          user.email,
          user.avatarURL)
    }
  }

  /**
    * Finds a user by its user ID.
    *
    * @param userID The ID of the user to find.
    * @return The found user or None if no user for the given ID could be found.
    */
  def find(userID: UUID) = {
    val query = for {
      dbUser <- slickUsers.filter(_.id === userID.toString)
      dbUserLoginInfo <- slickUserLoginInfos.filter(_.userID === dbUser.id)
      dbLoginInfo <- slickLoginInfos.filter(_.id === dbUserLoginInfo.loginInfoId)
    } yield (dbUser, dbLoginInfo)
    db.run(query.result).map(mapUser)
  }

  /**
    * Saves a user.
    *
    * @param user The user to save.
    * @return The saved user.
    */
  def save(user: User) = {
    val dbUser = DBUser(user.userID.toString, user.firstName, user.lastName, user.fullName, user.email, user.avatarURL)
    val dbLoginInfo = DBLoginInfo(None, user.loginInfo.head.providerID, user.loginInfo.head.providerKey)
    // We don't have the LoginInfo id so we try to get it first.
    // If there is no LoginInfo yet for this user we retrieve the id on insertion.
    val loginInfoAction = {
      val retrieveLoginInfo = slickLoginInfos.filter(
        loginInfo => loginInfo.providerID === user.loginInfo.head.providerID &&
          loginInfo.providerKey === user.loginInfo.head.providerKey).result.headOption
      val insertLoginInfo = slickLoginInfos.returning(slickLoginInfos.map(_.id)).
        into((loginInfo, id) => loginInfo.copy(id = Some(id))) += dbLoginInfo
      for {
        loginInfoOption <- retrieveLoginInfo
        loginInfo <- loginInfoOption.map(DBIO.successful(_)).getOrElse(insertLoginInfo)
      } yield loginInfo
    }
    // combine database actions to be run sequentially
    val actions = (for {
      _ <- slickUsers.insertOrUpdate(dbUser)
      loginInfo <- loginInfoAction
      _ <- slickUserLoginInfos += DBUserLoginInfo(dbUser.userID, loginInfo.id.get)
    } yield ()).transactionally
    // run actions and return user afterwards
    db.run(actions).map(_ => user)
  }
}
