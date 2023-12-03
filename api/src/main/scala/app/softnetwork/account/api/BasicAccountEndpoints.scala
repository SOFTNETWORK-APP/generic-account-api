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
import app.softnetwork.session.model.{SessionData, SessionDataCompanion, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.{RefreshTokenStorage, SessionConfig, SessionManager}
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext

trait BasicAccountEndpoints[SD <: SessionData with SessionDataDecorator[SD]]
    extends AccountEndpoints[BasicAccount, BasicAccountProfile, BasicAccountSignUp, SD] {
  self: BasicAccountApi[SD] with SchemaProvider with CsrfCheck =>
  override def accountEndpoints: ActorSystem[_] => AccountServiceEndpoints[BasicAccountSignUp, SD] =
    sys =>
      new BasicAccountServiceEndpoints[SD] with SessionMaterials[SD] {
        override protected def sessionType: Session.SessionType = self.sessionType
        override implicit def system: ActorSystem[_] = sys
        override implicit lazy val ec: ExecutionContext = sys.executionContext
        override implicit def sessionConfig: SessionConfig = self.sessionConfig
        override implicit def manager(implicit
          sessionConfig: SessionConfig,
          companion: SessionDataCompanion[SD]
        ): SessionManager[SD] = self.manager
        override protected val manifestWrapper: ManifestW = ManifestW()
        override def log: Logger = LoggerFactory getLogger getClass.getName
        override implicit def refreshTokenStorage: RefreshTokenStorage[SD] =
          self.refreshTokenStorage(sys)
        override implicit def companion: SessionDataCompanion[SD] = self.companion
      }

  override def oauthEndpoints: ActorSystem[_] => OAuthServiceEndpoints[SD] =
    sys =>
      new OAuthServiceEndpoints[SD] with BasicAccountTypeKey with SessionMaterials[SD] {
        override protected def sessionType: Session.SessionType = self.sessionType
        override implicit def system: ActorSystem[_] = sys
        override implicit lazy val ec: ExecutionContext = sys.executionContext
        override implicit def sessionConfig: SessionConfig = self.sessionConfig
        override implicit def manager(implicit
          sessionConfig: SessionConfig,
          companion: SessionDataCompanion[SD]
        ): SessionManager[SD] = self.manager
        override def log: Logger = LoggerFactory getLogger getClass.getName
        override implicit def refreshTokenStorage: RefreshTokenStorage[SD] =
          self.refreshTokenStorage(sys)
        override implicit def companion: SessionDataCompanion[SD] = self.companion
      }

  override def endpoints: ActorSystem[_] => List[Endpoint] = system =>
    super.endpoints(system) :+ accountSwagger(system) :+ oauthSwagger(system)
}
