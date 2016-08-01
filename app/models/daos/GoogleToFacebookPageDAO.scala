package models.daos

import javax.inject.Inject

import com.mohiva.play.silhouette.api.LoginInfo
import models.GoogleToFacebookPage
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GoogleToFacebookPageDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends DAOSlick {

  import pgslick.MyPostgresDriver.api._

  val log = Logger(this.getClass)

  def save(googleToFacebookPage: GoogleToFacebookPage) = {
    for {
      gtfp <- find(googleToFacebookPage.googleLoginInfo)
    } yield gtfp match {
      case Some(goo) => update(googleToFacebookPage.googleLoginInfo, googleToFacebookPage.facebookPageId,
        googleToFacebookPage.calendarId, googleToFacebookPage.name, googleToFacebookPage.eventNoun)
      case None => insert(googleToFacebookPage)
    }

  }

  def insert(googleToFacebookPage: GoogleToFacebookPage): Future[Unit] = {
    val action = (for {
      loginInfo <- loginInfoQuery(googleToFacebookPage.googleLoginInfo).result.headOption
      _ <- googleToFacebookPageTable += DBGoogleToFacebookPage(loginInfo.get.id.get, googleToFacebookPage
        .facebookPageId, googleToFacebookPage.accessToken, googleToFacebookPage.active,
        googleToFacebookPage.calendarId, googleToFacebookPage.name, googleToFacebookPage.eventNoun)
    } yield ()).transactionally
    db.run(action)
  }

  def googleToFacebookPageSubQuery(loginInfo: LoginInfo) = {
    googleToFacebookPageTable.filter(_.googleLoginInfoId in loginInfoQuery(loginInfo).map(_.id))
  }

  def updateAction(loginInfo: LoginInfo, calendarId: String, facebookPageId: Long, name: String,
                   eventNoun: String) = {
    googleToFacebookPageSubQuery(loginInfo)
      .map(gtfp => (gtfp.calendarId, gtfp.facebookPageId, gtfp.name, gtfp.eventNoun))
      .update((calendarId, facebookPageId, name, eventNoun))
  }

  def update(loginInfo: LoginInfo, facebookPageId: Long, calendarId: String, name: String,
             eventNoun: String): Future[Unit] = {
    db.run(updateAction(loginInfo, calendarId, facebookPageId, name, eventNoun)).map(_ => Unit)
  }

  def updateAction(loginInfo: LoginInfo, active: Boolean) = {
    googleToFacebookPageSubQuery(loginInfo)
      .map(gtfp => gtfp.active)
      .update(active)
  }

  def update(loginInfo: LoginInfo, active: Boolean): Future[Unit] = {
    db.run(updateAction(loginInfo, active)).map(_ => Unit)
  }

  def find(facebookPageId: Long) = {
    val query = (for {
      dbGoogleToFacebookPage <- googleToFacebookPageTable.filter(_.facebookPageId === facebookPageId).result.head
      loginInfo <- slickLoginInfos.filter(_.id === dbGoogleToFacebookPage.googleLoginInfoId).result.head
    } yield {
      (dbGoogleToFacebookPage, loginInfo)
    }).transactionally
    db.run(query) map { result =>
      GoogleToFacebookPage(LoginInfo(result._2.providerID, result._2.providerKey), result._1.facebookPageId, result
        ._1.accessToken, result._1.active, result._1.calendarId, result._1.name, result._1.eventNoun)
    }
  }

  def find(googleLoginInfo: LoginInfo): Future[Option[GoogleToFacebookPage]] = {
    val query = (for {
      loginInfo <- loginInfoQuery(googleLoginInfo).result.head
      dbGoogleToFacebookPage <- googleToFacebookPageTable.filter(_.googleLoginInfoId === loginInfo.id).result.headOption
    } yield {
      (dbGoogleToFacebookPage, loginInfo)
    }).transactionally
    db.run(query) map { entry =>
      entry._1 match {
        case Some(result) => Some(GoogleToFacebookPage(LoginInfo(entry._2.providerID, entry._2.providerKey),
          result.facebookPageId, result.accessToken, result.active, result.calendarId, result.name, result.eventNoun))
        case None => None
      }
    }
  }

}
