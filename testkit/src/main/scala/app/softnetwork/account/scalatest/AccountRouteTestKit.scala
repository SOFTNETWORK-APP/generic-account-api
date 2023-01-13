package app.softnetwork.account.scalatest

import akka.http.scaladsl.testkit.PersistenceScalatestRouteTest
import app.softnetwork.notification.model.Notification
import app.softnetwork.account.launch.AccountRoutes
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import org.scalatest.Suite

trait AccountRouteTestKit[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  N <: Notification
] extends PersistenceScalatestRouteTest
    with AccountRoutes[T, P]
    with AccountTestKit[T, P, N] { _: Suite => }
