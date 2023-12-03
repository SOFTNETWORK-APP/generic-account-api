package app.softnetwork.account.scalatest

import app.softnetwork.account.launch.AccountEndpoints
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}

trait AccountEndpointsTestKit[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  SU,
  SD <: SessionData with SessionDataDecorator[SD]
] extends AccountEndpoints[T, P, SU, SD] { _: SchemaProvider with CsrfCheck => }
