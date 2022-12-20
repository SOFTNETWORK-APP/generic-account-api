package app.softnetwork.account.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.handlers.AccountDao
import app.softnetwork.notification.launch.NotificationGuardian
import app.softnetwork.notification.model.Notification
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.account.persistence.query.AccountEventProcessorStreams.InternalAccountEvents2AccountProcessorStream
import app.softnetwork.account.persistence.typed.AccountBehavior
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.persistence.query.{EventProcessorStream, SchemaProvider}
import app.softnetwork.persistence.launch.PersistenceGuardian._
import app.softnetwork.session.launch.SessionGuardian

trait AccountGuardian[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  N <: Notification
] extends NotificationGuardian[N]
    with SessionGuardian { _: SchemaProvider =>

  def accountBehavior: ActorSystem[_] => AccountBehavior[T, P]

  def authEntities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    Seq(
      accountBehavior(sys)
    )

  /** initialize all entities
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    schedulerEntities(sys) ++ notificationEntities(sys) ++ sessionEntities(sys) ++ authEntities(sys)

  def internalAccountEvents2AccountProcessorStream
    : ActorSystem[_] => InternalAccountEvents2AccountProcessorStream

  def authEventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    Seq(internalAccountEvents2AccountProcessorStream(sys))

  /** initialize all event processor streams
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    schedulerEventProcessorStreams(sys) ++ notificationEventProcessorStreams(
      sys
    ) ++ authEventProcessorStreams(sys)

  def accountDao: AccountDao

  def initAuthSystem: ActorSystem[_] => Unit = system => {
    val root = AccountSettings.AdministratorsConfig.root
    accountDao.initAdminAccount(root.login, root.password)(system)
  }

  override def initSystem: ActorSystem[_] => Unit = system => {
    initAuthSystem(system)
    super.initSystem(system)
  }
}
