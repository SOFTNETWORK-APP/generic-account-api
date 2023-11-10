package app.softnetwork.account.persistence.typed

import akka.actor.typed.scaladsl.ActorContext
import app.softnetwork.account.handlers.{DefaultGenerator, Generator}
import app.softnetwork.account.message.{
  AccountCommand,
  AccountCreatedEvent,
  BasicAccountCreatedEvent,
  BasicAccountProfileUpdatedEvent,
  SignUp
}
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}

import java.time.Instant

trait BasicAccountBehavior extends AccountBehavior[BasicAccount, BasicAccountProfile] {
  self: Generator =>
  override protected def createAccount(entityId: String, cmd: SignUp)(implicit
    context: ActorContext[AccountCommand]
  ): Option[BasicAccount] =
    BasicAccount(cmd, Some(entityId))

  override protected def createProfileUpdatedEvent(
    uuid: String,
    profile: BasicAccountProfile,
    loginUpdated: Option[Boolean]
  )(implicit context: ActorContext[AccountCommand]): BasicAccountProfileUpdatedEvent =
    BasicAccountProfileUpdatedEvent(uuid, profile, loginUpdated).withLastUpdated(Instant.now())

  override protected def createAccountCreatedEvent(
    account: BasicAccount
  )(implicit context: ActorContext[AccountCommand]): AccountCreatedEvent[BasicAccount] =
    BasicAccountCreatedEvent(account)
}

object BasicAccountBehavior extends BasicAccountBehavior with DefaultGenerator {
  override def persistenceId: String = "Account"
}
