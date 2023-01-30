package app.softnetwork.account.scalatest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpHeader, HttpMessage, StatusCodes}
import app.softnetwork.api.server.config.ServerSettings.RootPath
import app.softnetwork.notification.model.Notification
import app.softnetwork.account.config.AccountSettings.Path
import app.softnetwork.account.handlers.MockBasicAccountDao
import app.softnetwork.account.message.{AccountCreated, BasicAccountSignUp, Login, Logout}
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.service.{AccountService, MockBasicAccountService}
import app.softnetwork.serialization._
import org.scalatest.Suite

import scala.util.{Failure, Success}

trait BasicAccountRouteTestKit
    extends BasicAccountTestKit
    with AccountRouteTestKit[BasicAccount, BasicAccountProfile, Notification] {
  _: Suite =>

  override def accountService: ActorSystem[_] => AccountService = system =>
    MockBasicAccountService(system)

  var cookies: Seq[HttpHeader] = Seq.empty

  def signUp(
    uuid: String,
    login: String,
    password: String,
    profile: Option[BasicAccountProfile] = None
  ): Boolean = {
    MockBasicAccountDao ?? (uuid, BasicAccountSignUp(login, password, profile = profile)) await {
      case _: AccountCreated => true
      case _                 => false
    } match {
      case Success(s) => s
      case Failure(f) =>
        logger.error(f.getMessage, f)
        false
    }
  }

  def signIn(login: String, password: String): Unit = {
    signOut()
    Post(s"/$RootPath/$Path/signIn", Login(login, password)) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      cookies = extractCookies(headers)
    }
  }

  def signOut(): Unit = {
    if (cookies.nonEmpty) {
      withCookies(Post(s"/$RootPath/$Path/signOut", Logout)) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        cookies = Seq.empty
      }
    }
  }

  def withCookies(request: HttpMessage): request.Self = {
    request.withHeaders(cookies: _*)
  }

}
