package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.account.launch.AccountRoutes
import app.softnetwork.account.model.{
  BasicAccount,
  BasicAccountProfile,
  DefaultAccountDetailsView,
  DefaultAccountView,
  DefaultProfileView
}
import app.softnetwork.account.service.{
  AccountService,
  BasicAccountService,
  BasicOAuthService,
  OAuthService
}
import app.softnetwork.api.server.ApiRoute
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.model.{SessionData, SessionDataCompanion, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.{RefreshTokenStorage, SessionConfig, SessionManager}
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext

trait BasicAccountRoutes[SD <: SessionData with SessionDataDecorator[SD]]
    extends AccountRoutes[
      BasicAccount,
      BasicAccountProfile,
      DefaultProfileView,
      DefaultAccountDetailsView,
      DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView],
      SD
    ] {
  self: BasicAccountApi[SD] with SchemaProvider with CsrfCheck =>
  override def accountService: ActorSystem[_] => AccountService[
    DefaultProfileView,
    DefaultAccountDetailsView,
    DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView],
    SD
  ] =
    sys =>
      new BasicAccountService[SD] with SessionMaterials[SD] {
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
          self.refreshTokenStorage(sys)
        override implicit def companion: SessionDataCompanion[SD] = self.companion
      }

  override def oauthService: ActorSystem[_] => OAuthService[SD] =
    sys =>
      new BasicOAuthService[SD] with SessionMaterials[SD] {
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
          self.refreshTokenStorage(sys)
        override implicit def companion: SessionDataCompanion[SD] = self.companion
      }

  override def apiRoutes: ActorSystem[_] => List[ApiRoute] = system =>
    super.apiRoutes(system) :+ accountSwagger(system) :+ oauthSwagger(system)
}
