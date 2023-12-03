package app.softnetwork.account.scalatest

import app.softnetwork.account.launch.AccountRoutes
import app.softnetwork.account.model.{
  Account,
  AccountDecorator,
  AccountDetailsView,
  AccountView,
  Profile,
  ProfileDecorator,
  ProfileView
}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}

trait AccountRoutesTestKit[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  PV <: ProfileView,
  DV <: AccountDetailsView,
  AV <: AccountView[PV, DV],
  SD <: SessionData with SessionDataDecorator[SD]
] extends AccountRoutes[T, P, PV, DV, AV, SD] { _: SchemaProvider with CsrfCheck => }
