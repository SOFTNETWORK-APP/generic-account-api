package app.softnetwork.account.scalatest

import app.softnetwork.account.launch.AccountRoutes
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.persistence.schema.SchemaProvider

trait AccountRoutesTestKit[T <: Account with AccountDecorator, P <: Profile with ProfileDecorator]
    extends AccountRoutes[T, P] { _: SchemaProvider => }
