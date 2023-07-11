package app.softnetwork.account.scalatest

import app.softnetwork.account.launch.AccountEndpoints
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.persistence.schema.SchemaProvider

trait AccountEndpointsTestKit[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  SU
] extends AccountEndpoints[T, P, SU] { _: SchemaProvider => }
