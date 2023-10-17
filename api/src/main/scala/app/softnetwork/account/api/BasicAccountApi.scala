package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.account.handlers.{AccountDao, BasicAccountDao, BasicAccountTypeKey}
import app.softnetwork.account.launch.AccountApplication
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.persistence.query.AccountEventProcessorStreams.InternalAccountEvents2AccountProcessorStream
import app.softnetwork.account.persistence.typed.{AccountBehavior, BasicAccountBehavior}
import app.softnetwork.account.service.BasicAccountServiceEndpoints
import app.softnetwork.api.server.{ApiRoutes, SwaggerEndpoint}
import app.softnetwork.persistence.jdbc.query.{JdbcJournalProvider, JdbcOffsetProvider}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.service.SessionEndpoints
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}

trait BasicAccountApi extends AccountApplication[BasicAccount, BasicAccountProfile] {
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

  def accountSwagger: ActorSystem[_] => SwaggerEndpoint = sys =>
    new BasicAccountServiceEndpoints with SwaggerEndpoint {
      lazy val log: Logger = LoggerFactory getLogger getClass.getName
      override implicit def system: ActorSystem[_] = sys
      override def sessionEndpoints: SessionEndpoints = self.sessionEndpoints(sys)
      override protected val manifestWrapper: ManifestW = ManifestW()
      override val applicationVersion: String = self.systemVersion()
    }
}
