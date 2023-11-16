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
import app.softnetwork.session.scalatest.{
  OneOffCookieSessionServiceTestKit,
  OneOffHeaderSessionServiceTestKit,
  RefreshableCookieSessionServiceTestKit,
  RefreshableHeaderSessionServiceTestKit
}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.{SessionConfig, SessionManager}
import org.scalatest.Suite
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext

trait BasicAccountRoutesTestKit
    extends AccountRoutesTestKit[
      BasicAccount,
      BasicAccountProfile,
      DefaultProfileView,
      DefaultAccountDetailsView,
      DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]
    ] {
  self: SchemaProvider with CsrfCheck with SessionMaterials =>

  implicit def sessionConfig: SessionConfig

  override def accountService: ActorSystem[_] => AccountService[
    DefaultProfileView,
    DefaultAccountDetailsView,
    DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]
  ] =
    sys =>
      new MockBasicAccountService with SessionMaterials {
        override implicit def manager(implicit
          sessionConfig: SessionConfig
        ): SessionManager[Session] = self.manager
        override protected def sessionType: Session.SessionType = self.sessionType
        override implicit def system: ActorSystem[_] = sys
        override implicit lazy val ec: ExecutionContext = sys.executionContext
        override implicit def sessionConfig: SessionConfig = self.sessionConfig
        override protected val manifestWrapper: ManifestW = ManifestW()
        override def log: Logger = LoggerFactory getLogger getClass.getName
      }

  override def oauthService: ActorSystem[_] => OAuthService =
    sys =>
      new MockOAuthService with SessionMaterials {
        override implicit def manager(implicit
          sessionConfig: SessionConfig
        ): SessionManager[Session] = self.manager
        override protected def sessionType: Session.SessionType = self.sessionType
        override implicit def system: ActorSystem[_] = sys
        override implicit lazy val ec: ExecutionContext = sys.executionContext
        override implicit def sessionConfig: SessionConfig = self.sessionConfig
        override def log: Logger = LoggerFactory getLogger getClass.getName
      }
}

trait OneOfCookieSessionBasicAccountRoutesTestKit
    extends OneOffCookieSessionServiceTestKit
    with BasicAccountRoutesTestKit { _: Suite with SessionMaterials => }

trait OneOfHeaderSessionBasicAccountRoutesTestKit
    extends OneOffHeaderSessionServiceTestKit
    with BasicAccountRoutesTestKit { _: Suite with SessionMaterials => }

trait RefreshableCookieSessionBasicAccountRoutesTestKit
    extends RefreshableCookieSessionServiceTestKit
    with BasicAccountRoutesTestKit { _: Suite with SessionMaterials => }

trait RefreshableHeaderSessionBasicAccountRoutesTestKit
    extends RefreshableHeaderSessionServiceTestKit
    with BasicAccountRoutesTestKit { _: Suite with SessionMaterials => }
