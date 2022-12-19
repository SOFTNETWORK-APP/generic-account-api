package app.softnetwork.account.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.model.Notification
import app.softnetwork.notification.scalatest.NotificationTestKit
import app.softnetwork.account.config.Settings
import app.softnetwork.account.launch.AccountGuardian
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import org.scalatest.Suite

trait AccountTestKit[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  N <: Notification
] extends NotificationTestKit[N]
    with AccountGuardian[T, P, N] { _: Suite =>
  implicit lazy val tsystem: ActorSystem[_] = typedSystem()

  /** @return
    *   roles associated with this node
    */
  override def roles: Seq[String] = super.roles :+ Settings.AkkaNodeRole

}
