package app.softnetwork.account.launch

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.account.model.{
  Account,
  AccountDecorator,
  AccountDetailsView,
  AccountView,
  Profile,
  ProfileDecorator,
  ProfileView
}
import app.softnetwork.account.serialization.accountFormats
import app.softnetwork.account.service.AccountService
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.service.SessionService
import org.json4s.Formats

trait AccountRoutes[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  PV <: ProfileView,
  DV <: AccountDetailsView,
  AV <: AccountView[PV, DV]
] extends ApiRoutes
    with AccountGuardian[T, P] { _: SchemaProvider =>

  override implicit def formats: Formats = accountFormats

  def sessionService: ActorSystem[_] => SessionService

  def accountService: ActorSystem[_] => AccountService[PV, DV, AV]

  override def apiRoutes(system: ActorSystem[_]): Route = accountService(system).route

}
