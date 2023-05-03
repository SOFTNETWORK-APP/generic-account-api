package app.softnetwork.account.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.handlers.AccountDao
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.account.persistence.query.AccountEventProcessorStreams.{
  InternalAccountEvents2AccountProcessorStream,
  Scheduler2AccountProcessorStream
}
import app.softnetwork.account.persistence.typed.AccountBehavior
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.persistence.query.EventProcessorStream
import app.softnetwork.persistence.launch.PersistenceGuardian._
import app.softnetwork.session.launch.SessionGuardian

trait AccountGuardian[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator
] extends SessionGuardian {

  def accountBehavior: ActorSystem[_] => AccountBehavior[T, P]

  def accountEntities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    Seq(
      accountBehavior(sys)
    )

  /** initialize all entities
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    sessionEntities(sys) ++ accountEntities(
      sys
    )

  def internalAccountEvents2AccountProcessorStream
    : ActorSystem[_] => InternalAccountEvents2AccountProcessorStream

  def scheduler2AccountProcessorStream: ActorSystem[_] => Option[Scheduler2AccountProcessorStream] =
    _ => None

  def accountEventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    Seq(internalAccountEvents2AccountProcessorStream(sys)) ++ Seq(
      scheduler2AccountProcessorStream(sys)
    ).flatten

  /** initialize all event processor streams
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    accountEventProcessorStreams(sys)

  def accountDao: AccountDao

  def initAccountSystem: ActorSystem[_] => Unit = system => {
    val root = AccountSettings.AdministratorsConfig.root
    accountDao.initAdminAccount(root.login, root.password)(system)
  }

  override def initSystem: ActorSystem[_] => Unit = system => {
    initAccountSystem(system)
    super.initSystem(system)
  }
}
