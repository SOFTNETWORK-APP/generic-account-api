package app.softnetwork.account.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.{ApiRoute, ApiRoutes}
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
import app.softnetwork.session.CsrfCheck
import org.json4s.Formats

trait AccountRoutes[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  PV <: ProfileView,
  DV <: AccountDetailsView,
  AV <: AccountView[PV, DV]
] extends ApiRoutes
    with AccountGuardian[T, P] { _: SchemaProvider with CsrfCheck =>

  override implicit def formats: Formats = accountFormats

  def accountService: ActorSystem[_] => AccountService[PV, DV, AV]

  override def apiRoutes: ActorSystem[_] => List[ApiRoute] =
    system => List(accountService(system))

}
