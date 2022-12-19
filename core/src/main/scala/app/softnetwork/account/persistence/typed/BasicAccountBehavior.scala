package app.softnetwork.account.persistence.typed

import app.softnetwork.account.handlers.{DefaultGenerator, Generator}
import app.softnetwork.account.message.{
  AccountCreatedEvent,
  BasicAccountCreatedEvent,
  BasicAccountProfileUpdatedEvent,
  SignUp
}
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.persistence.now

trait BasicAccountBehavior extends AccountBehavior[BasicAccount, BasicAccountProfile] {
  self: Generator =>
  override protected def createAccount(entityId: String, cmd: SignUp): Option[BasicAccount] =
    BasicAccount(cmd, Some(entityId))

  override protected def createProfileUpdatedEvent(
    uuid: String,
    profile: BasicAccountProfile,
    loginUpdated: Option[Boolean]
  ): BasicAccountProfileUpdatedEvent =
    BasicAccountProfileUpdatedEvent(uuid, profile, loginUpdated).withLastUpdated(now())

  override protected def createAccountCreatedEvent(
    account: BasicAccount
  ): AccountCreatedEvent[BasicAccount] =
    BasicAccountCreatedEvent(account)
}

object BasicAccountBehavior extends BasicAccountBehavior with DefaultGenerator {
  override def persistenceId: String = "Account"
}
