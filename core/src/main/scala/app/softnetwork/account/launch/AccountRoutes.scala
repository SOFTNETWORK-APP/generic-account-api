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
import app.softnetwork.account.service.{AccountService, OAuthService}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import org.json4s.Formats

trait AccountRoutes[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  PV <: ProfileView,
  DV <: AccountDetailsView,
  AV <: AccountView[PV, DV],
  SD <: SessionData with SessionDataDecorator[SD]
] extends ApiRoutes
    with AccountGuardian[T, P] { _: SchemaProvider with CsrfCheck =>

  override implicit def formats: Formats = accountFormats

  def accountService: ActorSystem[_] => AccountService[PV, DV, AV, SD]

  def oauthService: ActorSystem[_] => OAuthService[SD]

  override def apiRoutes: ActorSystem[_] => List[ApiRoute] =
    system => List(accountService(system), oauthService(system))

}
