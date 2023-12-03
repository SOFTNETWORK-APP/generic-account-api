package app.softnetwork.account.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.account.message.BasicAccountSignUp
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.service.{
  AccountServiceEndpoints,
  MockBasicAccountServiceEndpoints,
  MockOAuthServiceEndpoints,
  OAuthServiceEndpoints
}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.model.{SessionData, SessionDataCompanion, SessionDataDecorator}
import app.softnetwork.session.scalatest.{
  OneOffCookieSessionEndpointsTestKit,
  OneOffHeaderSessionEndpointsTestKit,
  RefreshableCookieSessionEndpointsTestKit,
  RefreshableHeaderSessionEndpointsTestKit
}
import app.softnetwork.session.service.SessionMaterials
import app.softnetwork.session.{CsrfCheck, CsrfCheckHeader}
import com.softwaremill.session.{RefreshTokenStorage, SessionConfig, SessionManager}
import org.scalatest.Suite
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext

trait BasicAccountEndpointsTestKit[SD <: SessionData with SessionDataDecorator[SD]]
    extends AccountEndpointsTestKit[BasicAccount, BasicAccountProfile, BasicAccountSignUp, SD] {
  self: SchemaProvider with CsrfCheck with SessionMaterials[SD] =>

  implicit def sessionConfig: SessionConfig

  implicit def companion: SessionDataCompanion[SD]

  override def accountEndpoints
    : ActorSystem[_] => AccountServiceEndpoints[BasicAccountSignUp, SD] = { sys =>
    new MockBasicAccountServiceEndpoints[SD] with SessionMaterials[SD] {
      override implicit def manager(implicit
        sessionConfig: SessionConfig,
        companion: SessionDataCompanion[SD]
      ): SessionManager[SD] = self.manager
      override protected def sessionType: Session.SessionType = self.sessionType
      override implicit def system: ActorSystem[_] = sys
      override implicit lazy val ec: ExecutionContext = sys.executionContext
      override implicit def sessionConfig: SessionConfig = self.sessionConfig
      override protected val manifestWrapper: ManifestW = ManifestW()
      override def log: Logger = LoggerFactory getLogger getClass.getName
      override implicit def refreshTokenStorage: RefreshTokenStorage[SD] = self.refreshTokenStorage
      override implicit def companion: SessionDataCompanion[SD] = self.companion
    }
  }

  override def oauthEndpoints: ActorSystem[_] => OAuthServiceEndpoints[SD] =
    sys =>
      new MockOAuthServiceEndpoints[SD] with SessionMaterials[SD] {
        override implicit def manager(implicit
          sessionConfig: SessionConfig,
          companion: SessionDataCompanion[SD]
        ): SessionManager[SD] = self.manager
        override protected def sessionType: Session.SessionType = self.sessionType
        override implicit def system: ActorSystem[_] = sys
        override implicit lazy val ec: ExecutionContext = sys.executionContext
        override implicit def sessionConfig: SessionConfig = self.sessionConfig
        override def log: Logger = LoggerFactory getLogger getClass.getName
        override implicit def refreshTokenStorage: RefreshTokenStorage[SD] =
          self.refreshTokenStorage
        override implicit def companion: SessionDataCompanion[SD] = self.companion
      }
}

trait OneOfCookieSessionBasicAccountEndpointsTestKit[SD <: SessionData with SessionDataDecorator[
  SD
]] extends OneOffCookieSessionEndpointsTestKit[SD]
    with BasicAccountEndpointsTestKit[SD]
    with CsrfCheckHeader { _: Suite with SessionMaterials[SD] => }

trait OneOfHeaderSessionBasicAccountEndpointsTestKit[SD <: SessionData with SessionDataDecorator[
  SD
]] extends OneOffHeaderSessionEndpointsTestKit[SD]
    with BasicAccountEndpointsTestKit[SD]
    with CsrfCheckHeader { _: Suite with SessionMaterials[SD] => }

trait RefreshableCookieSessionBasicAccountEndpointsTestKit[
  SD <: SessionData with SessionDataDecorator[SD]
] extends RefreshableCookieSessionEndpointsTestKit[SD]
    with BasicAccountEndpointsTestKit[SD]
    with CsrfCheckHeader { _: Suite with SessionMaterials[SD] => }

trait RefreshableHeaderSessionBasicAccountEndpointsTestKit[
  SD <: SessionData with SessionDataDecorator[SD]
] extends RefreshableHeaderSessionEndpointsTestKit[SD]
    with BasicAccountEndpointsTestKit[SD]
    with CsrfCheckHeader { _: Suite with SessionMaterials[SD] => }
