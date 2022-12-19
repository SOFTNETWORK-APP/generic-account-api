package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.api.AllNotificationsApi
import app.softnetwork.notification.model.Notification
import app.softnetwork.account.handlers.{AccountDao, BasicAccountDao, BasicAccountTypeKey}
import app.softnetwork.account.launch.AccountApplication
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.persistence.query.AccountEventProcessorStreams.InternalAccountEvents2AccountProcessorStream
import app.softnetwork.account.persistence.typed.{AccountBehavior, BasicAccountBehavior}
import app.softnetwork.account.service.{AccountService, BasicAccountService}
import app.softnetwork.persistence.jdbc.query.{JdbcJournalProvider, JdbcSchema, JdbcSchemaProvider}

trait BasicAccountApi
    extends AllNotificationsApi
    with AccountApplication[BasicAccount, BasicAccountProfile, Notification]
    with JdbcSchemaProvider {

  override def accountDao: AccountDao = BasicAccountDao

  override def accountService: ActorSystem[_] => AccountService = sys => BasicAccountService(sys)

  override def accountBehavior
    : ActorSystem[_] => AccountBehavior[BasicAccount, BasicAccountProfile] = _ =>
    BasicAccountBehavior

  override def internalAccountEvents2AccountProcessorStream
    : ActorSystem[_] => InternalAccountEvents2AccountProcessorStream = sys =>
    new InternalAccountEvents2AccountProcessorStream
      with BasicAccountTypeKey
      with JdbcJournalProvider
      with JdbcSchemaProvider {
      override def tag: String = s"${BasicAccountBehavior.persistenceId}-to-internal"
      override lazy val schemaType: JdbcSchema.SchemaType = jdbcSchemaType
      override implicit def system: ActorSystem[_] = sys
    }

}
