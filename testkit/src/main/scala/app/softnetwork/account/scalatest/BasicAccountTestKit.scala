package app.softnetwork.account.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.model.Notification
import app.softnetwork.notification.scalatest.AllNotificationsTestKit
import app.softnetwork.account.handlers.{AccountDao, MockBasicAccountDao, MockBasicAccountHandler}
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.persistence.query.AccountEventProcessorStreams.InternalAccountEvents2AccountProcessorStream
import app.softnetwork.account.persistence.typed.{AccountBehavior, MockBasicAccountBehavior}
import app.softnetwork.persistence.query.{InMemoryJournalProvider, InMemoryOffsetProvider}
import org.scalatest.Suite
import org.slf4j.{Logger, LoggerFactory}

trait BasicAccountTestKit
    extends AccountTestKit[BasicAccount, BasicAccountProfile, Notification]
    with AllNotificationsTestKit {
  _: Suite =>
  override def accountBehavior
    : ActorSystem[_] => AccountBehavior[BasicAccount, BasicAccountProfile] = _ =>
    MockBasicAccountBehavior

  override def internalAccountEvents2AccountProcessorStream
    : ActorSystem[_] => InternalAccountEvents2AccountProcessorStream = sys =>
    new InternalAccountEvents2AccountProcessorStream
      with MockBasicAccountHandler
      with InMemoryJournalProvider
      with InMemoryOffsetProvider {
      lazy val log: Logger = LoggerFactory getLogger getClass.getName
      override def tag: String = s"${MockBasicAccountBehavior.persistenceId}-to-internal"
      override lazy val forTests: Boolean = true
      override implicit def system: ActorSystem[_] = sys
    }

  override def accountDao: AccountDao = MockBasicAccountDao
}
