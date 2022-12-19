package app.softnetwork.account.launch

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.notification.model.Notification
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.account.serialization.authFormats
import app.softnetwork.account.service.AccountService
import app.softnetwork.persistence.query.SchemaProvider
import org.json4s.Formats

trait AccountRoutes[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  N <: Notification
] extends ApiRoutes
    with AccountGuardian[T, P, N] {
  _: SchemaProvider =>

  override implicit def formats: Formats = authFormats

  def accountService: ActorSystem[_] => AccountService

  override def apiRoutes(system: ActorSystem[_]): Route = accountService(system).route

}
