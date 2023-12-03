package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.handlers.{AccountDao, BasicAccountDao, BasicAccountTypeKey}
import app.softnetwork.account.launch.AccountApplication
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.persistence.query.AccountEventProcessorStreams.InternalAccountEvents2AccountProcessorStream
import app.softnetwork.account.persistence.typed.{AccountBehavior, BasicAccountBehavior}
import app.softnetwork.account.service.{BasicAccountServiceEndpoints, OAuthServiceEndpoints}
import app.softnetwork.api.server.{ApiRoutes, SwaggerEndpoint}
import app.softnetwork.persistence.jdbc.query.{JdbcJournalProvider, JdbcOffsetProvider}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.config.Settings
import app.softnetwork.session.model.{
  SessionData,
  SessionDataCompanion,
  SessionDataDecorator,
  SessionManagers
}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.{RefreshTokenStorage, SessionConfig, SessionManager}
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session
import sttp.tapir.swagger.SwaggerUIOptions

import scala.concurrent.ExecutionContext

trait BasicAccountApi[SD <: SessionData with SessionDataDecorator[SD]]
    extends AccountApplication[BasicAccount, BasicAccountProfile] {
  self: SchemaProvider with ApiRoutes with CsrfCheck =>

  override def accountDao: AccountDao = BasicAccountDao

  override def accountBehavior
    : ActorSystem[_] => AccountBehavior[BasicAccount, BasicAccountProfile] = _ =>
    BasicAccountBehavior

  override def internalAccountEvents2AccountProcessorStream
    : ActorSystem[_] => InternalAccountEvents2AccountProcessorStream = sys =>
    new InternalAccountEvents2AccountProcessorStream
      with BasicAccountTypeKey
      with JdbcJournalProvider
      with JdbcOffsetProvider {
      override def config: Config = BasicAccountApi.this.config
      override def tag: String = s"${BasicAccountBehavior.persistenceId}-to-internal"
      override implicit def system: ActorSystem[_] = sys
    }

  implicit def sessionConfig: SessionConfig = Settings.Session.DefaultSessionConfig

  implicit def companion: SessionDataCompanion[SD]

  override protected def sessionType: Session.SessionType =
    Settings.Session.SessionContinuityAndTransport

  protected def manager: SessionManager[SD] = SessionManagers.basic

  protected def refreshTokenStorage: ActorSystem[_] => RefreshTokenStorage[SD]

  def accountSwagger: ActorSystem[_] => SwaggerEndpoint = sys =>
    new BasicAccountServiceEndpoints[SD] with SwaggerEndpoint with SessionMaterials[SD] {
      lazy val log: Logger = LoggerFactory getLogger getClass.getName
      override implicit def system: ActorSystem[_] = sys
      override implicit lazy val ec: ExecutionContext = sys.executionContext
      override protected def sessionType: Session.SessionType = self.sessionType
      override implicit def sessionConfig: SessionConfig = self.sessionConfig
      override implicit def manager(implicit
        sessionConfig: SessionConfig,
        companion: SessionDataCompanion[SD]
      ): SessionManager[SD] = self.manager
      override protected val manifestWrapper: ManifestW = ManifestW()
      override implicit def refreshTokenStorage: RefreshTokenStorage[SD] =
        self.refreshTokenStorage(sys)
      override implicit def companion: SessionDataCompanion[SD] = self.companion
      override val applicationVersion: String = self.systemVersion()
      override val swaggerUIOptions: SwaggerUIOptions =
        SwaggerUIOptions.default.pathPrefix(List("swagger", AccountSettings.Path))
    }

  def oauthSwagger: ActorSystem[_] => SwaggerEndpoint =
    sys =>
      new OAuthServiceEndpoints[SD]
        with BasicAccountTypeKey
        with SwaggerEndpoint
        with SessionMaterials[SD] {
        override implicit def system: ActorSystem[_] = sys
        override implicit lazy val ec: ExecutionContext = sys.executionContext
        override protected def sessionType: Session.SessionType = self.sessionType
        override implicit def sessionConfig: SessionConfig = self.sessionConfig
        override implicit def manager(implicit
          sessionConfig: SessionConfig,
          companion: SessionDataCompanion[SD]
        ): SessionManager[SD] = self.manager
        override def log: Logger = LoggerFactory getLogger getClass.getName
        override implicit def refreshTokenStorage: RefreshTokenStorage[SD] =
          self.refreshTokenStorage(sys)
        override implicit def companion: SessionDataCompanion[SD] = self.companion
        override val applicationVersion: String = self.systemVersion()
        override val swaggerUIOptions: SwaggerUIOptions =
          SwaggerUIOptions.default.pathPrefix(List("swagger", AccountSettings.OAuthPath))
      }
}
