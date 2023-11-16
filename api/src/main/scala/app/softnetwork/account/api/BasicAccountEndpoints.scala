package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.account.handlers.BasicAccountTypeKey
import app.softnetwork.account.launch.AccountEndpoints
import app.softnetwork.account.message.BasicAccountSignUp
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.service.{
  AccountServiceEndpoints,
  BasicAccountServiceEndpoints,
  OAuthServiceEndpoints
}
import app.softnetwork.api.server.Endpoint
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.service.{SessionEndpoints, SessionMaterials}
import com.softwaremill.session.{SessionConfig, SessionManager}
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext

trait BasicAccountEndpoints
    extends AccountEndpoints[BasicAccount, BasicAccountProfile, BasicAccountSignUp] {
  self: BasicAccountApi with SchemaProvider with CsrfCheck =>
  override def accountEndpoints: ActorSystem[_] => AccountServiceEndpoints[BasicAccountSignUp] =
    sys =>
      new BasicAccountServiceEndpoints with SessionMaterials {
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

  override def oauthEndpoints: ActorSystem[_] => OAuthServiceEndpoints =
    sys =>
      new OAuthServiceEndpoints with BasicAccountTypeKey with SessionMaterials {
        override implicit def manager(implicit
          sessionConfig: SessionConfig
        ): SessionManager[Session] = self.manager
        override protected def sessionType: Session.SessionType = self.sessionType
        override implicit def system: ActorSystem[_] = sys
        override implicit lazy val ec: ExecutionContext = sys.executionContext
        override implicit def sessionConfig: SessionConfig = self.sessionConfig
        override def log: Logger = LoggerFactory getLogger getClass.getName
      }

  override def endpoints: ActorSystem[_] => List[Endpoint] = system =>
    super.endpoints(system) :+ accountSwagger(system) :+ oauthSwagger(system)
}
