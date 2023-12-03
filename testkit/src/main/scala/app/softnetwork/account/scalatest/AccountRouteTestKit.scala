package app.softnetwork.account.scalatest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import app.softnetwork.account.config.AccountSettings.Path
import app.softnetwork.account.message.{Login, Logout, SignUp}
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.api.server.config.ServerSettings.RootPath
import app.softnetwork.serialization._
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.scalatest.SessionTestKit
import app.softnetwork.session.service.SessionMaterials
import org.scalatest.Suite

import scala.language.implicitConversions

trait AccountRouteTestKit[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  SD <: SessionData with SessionDataDecorator[SD]
] extends SessionTestKit[SD]
    with AccountTestKit[T, P] { _: Suite with ApiRoutes with SessionMaterials[SD] =>

  override implicit lazy val ts: ActorSystem[_] = typedSystem()

  override def beforeAll(): Unit = {
    super.beforeAll()
    // pre load routes
    apiRoutes(typedSystem())
  }

  def signUp(
    login: String,
    password: String,
    profile: Option[P] = None,
    body: => T
  ): T = {
    Post(
      s"/$RootPath/$Path/signUp",
      implicitly[SignUp]((login, password, profile))
    ) ~> routes ~> check(body)
  }

  def signIn(login: String, password: String): Unit = {
    signOut()
    Post(s"/$RootPath/$Path/signIn", Login(login, password)) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      httpHeaders = extractHeaders(headers)
    }
  }

  def signOut(): Unit = {
    if (httpHeaders.nonEmpty) {
      withHeaders(Post(s"/$RootPath/$Path/signOut", Logout)) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        httpHeaders = extractHeaders(headers)
      }
    }
  }

}
