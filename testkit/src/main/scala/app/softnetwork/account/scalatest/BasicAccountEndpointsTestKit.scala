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
import app.softnetwork.session.scalatest.{
  OneOffCookieSessionEndpointsTestKit,
  OneOffHeaderSessionEndpointsTestKit,
  RefreshableCookieSessionEndpointsTestKit,
  RefreshableHeaderSessionEndpointsTestKit
}
import app.softnetwork.session.service.SessionMaterials
import app.softnetwork.session.{CsrfCheck, CsrfCheckHeader}
import com.softwaremill.session.{SessionConfig, SessionManager}
import org.scalatest.Suite
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext

trait BasicAccountEndpointsTestKit
    extends AccountEndpointsTestKit[BasicAccount, BasicAccountProfile, BasicAccountSignUp] {
  self: SchemaProvider with CsrfCheck with SessionMaterials =>

  implicit def sessionConfig: SessionConfig

  override def accountEndpoints: ActorSystem[_] => AccountServiceEndpoints[BasicAccountSignUp] = {
    sys =>
      new MockBasicAccountServiceEndpoints with SessionMaterials {
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
  }

  override def oauthEndpoints: ActorSystem[_] => OAuthServiceEndpoints =
    sys =>
      new MockOAuthServiceEndpoints with SessionMaterials {
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

trait OneOfCookieSessionBasicAccountEndpointsTestKit
    extends OneOffCookieSessionEndpointsTestKit
    with BasicAccountEndpointsTestKit
    with CsrfCheckHeader { _: Suite with SessionMaterials => }

trait OneOfHeaderSessionBasicAccountEndpointsTestKit
    extends OneOffHeaderSessionEndpointsTestKit
    with BasicAccountEndpointsTestKit
    with CsrfCheckHeader { _: Suite with SessionMaterials => }

trait RefreshableCookieSessionBasicAccountEndpointsTestKit
    extends RefreshableCookieSessionEndpointsTestKit
    with BasicAccountEndpointsTestKit
    with CsrfCheckHeader { _: Suite with SessionMaterials => }

trait RefreshableHeaderSessionBasicAccountEndpointsTestKit
    extends RefreshableHeaderSessionEndpointsTestKit
    with BasicAccountEndpointsTestKit
    with CsrfCheckHeader { _: Suite with SessionMaterials => }
