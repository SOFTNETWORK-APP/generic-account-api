package app.softnetwork.account.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.scalatest.AllNotificationsTestKit
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.launch.AccountGuardian
import app.softnetwork.account.message.SignUp
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.persistence.query.EventProcessorStream
import org.scalatest.Suite

trait AccountTestKit[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator
] extends AccountGuardian[T, P]
    with AllNotificationsTestKit { _: Suite =>
  implicit lazy val tsystem: ActorSystem[_] = typedSystem()

  /** @return
    *   roles associated with this node
    */
  type LPP = (String, String, Option[P])

  implicit def lppToSignUp: LPP => SignUp

  override def roles: Seq[String] = super.roles :+ AccountSettings.AkkaNodeRole

  /** initialize all entities
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    sessionEntities(sys) ++ accountEntities(sys) ++ schedulerEntities(sys) ++ notificationEntities(
      sys
    )

  /** initialize all event processor streams
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    accountEventProcessorStreams(sys) ++ schedulerEventProcessorStreams(
      sys
    ) ++ notificationEventProcessorStreams(sys)

  override def initSystem: ActorSystem[_] => Unit = system => {
    initAccountSystem(system)
    initSchedulerSystem(system)
  }
}
