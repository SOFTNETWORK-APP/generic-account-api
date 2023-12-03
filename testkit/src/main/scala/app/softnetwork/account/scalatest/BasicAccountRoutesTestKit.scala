package app.softnetwork.account.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.account.model.{
  BasicAccount,
  BasicAccountProfile,
  DefaultAccountDetailsView,
  DefaultAccountView,
  DefaultProfileView
}
import app.softnetwork.account.service.{
  AccountService,
  MockBasicAccountService,
  MockOAuthService,
  OAuthService
}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.model.{SessionData, SessionDataCompanion, SessionDataDecorator}
import app.softnetwork.session.scalatest.{
  OneOffCookieSessionServiceTestKit,
  OneOffHeaderSessionServiceTestKit,
  RefreshableCookieSessionServiceTestKit,
  RefreshableHeaderSessionServiceTestKit
}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.{RefreshTokenStorage, SessionConfig, SessionManager}
import org.scalatest.Suite
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext

trait BasicAccountRoutesTestKit[SD <: SessionData with SessionDataDecorator[SD]]
    extends AccountRoutesTestKit[
      BasicAccount,
      BasicAccountProfile,
      DefaultProfileView,
      DefaultAccountDetailsView,
      DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView],
      SD
    ] {
  self: SchemaProvider with CsrfCheck with SessionMaterials[SD] =>

  implicit def sessionConfig: SessionConfig

  implicit def companion: SessionDataCompanion[SD]
  override def accountService: ActorSystem[_] => AccountService[
    DefaultProfileView,
    DefaultAccountDetailsView,
    DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView],
    SD
  ] =
    sys =>
      new MockBasicAccountService[SD] with SessionMaterials[SD] {
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
        override implicit def refreshTokenStorage: RefreshTokenStorage[SD] =
          self.refreshTokenStorage
        override implicit def companion: SessionDataCompanion[SD] = self.companion
      }

  override def oauthService: ActorSystem[_] => OAuthService[SD] =
    sys =>
      new MockOAuthService[SD] with SessionMaterials[SD] {
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

trait OneOfCookieSessionBasicAccountRoutesTestKit[SD <: SessionData with SessionDataDecorator[SD]]
    extends OneOffCookieSessionServiceTestKit[SD]
    with BasicAccountRoutesTestKit[SD] { _: Suite with SessionMaterials[SD] => }

trait OneOfHeaderSessionBasicAccountRoutesTestKit[SD <: SessionData with SessionDataDecorator[SD]]
    extends OneOffHeaderSessionServiceTestKit[SD]
    with BasicAccountRoutesTestKit[SD] { _: Suite with SessionMaterials[SD] => }

trait RefreshableCookieSessionBasicAccountRoutesTestKit[SD <: SessionData with SessionDataDecorator[
  SD
]] extends RefreshableCookieSessionServiceTestKit[SD]
    with BasicAccountRoutesTestKit[SD] { _: Suite with SessionMaterials[SD] => }

trait RefreshableHeaderSessionBasicAccountRoutesTestKit[SD <: SessionData with SessionDataDecorator[
  SD
]] extends RefreshableHeaderSessionServiceTestKit[SD]
    with BasicAccountRoutesTestKit[SD] { _: Suite with SessionMaterials[SD] => }
