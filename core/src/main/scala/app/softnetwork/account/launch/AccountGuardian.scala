package app.softnetwork.account.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.launch.NotificationGuardian
import app.softnetwork.notification.model.Notification
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.account.persistence.query.AccountEventProcessorStreams.InternalAccountEvents2AccountProcessorStream
import app.softnetwork.account.persistence.typed.AccountBehavior
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.persistence.query.{EventProcessorStream, SchemaProvider}
import app.softnetwork.session.launch.SessionGuardian

trait AccountGuardian[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  N <: Notification
] extends NotificationGuardian[N]
    with SessionGuardian { _: SchemaProvider =>

  import app.softnetwork.persistence.launch.PersistenceGuardian._

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

}
