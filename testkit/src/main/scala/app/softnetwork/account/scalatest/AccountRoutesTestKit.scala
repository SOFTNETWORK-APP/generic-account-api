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

trait AccountRoutesTestKit[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  PV <: ProfileView,
  DV <: AccountDetailsView,
  AV <: AccountView[PV, DV]
] extends AccountRoutes[T, P, PV, DV, AV] { _: SchemaProvider => }
