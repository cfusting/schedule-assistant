package silhouette

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import models.User

trait CookieEnv extends Env {

  type I = User
  type A = CookieAuthenticator

}
