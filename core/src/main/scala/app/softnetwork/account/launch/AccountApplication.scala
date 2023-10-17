package app.softnetwork.account.launch

import app.softnetwork.api.server.launch.Application
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck

/** Created by smanciot on 22/03/2018.
  */
trait AccountApplication[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator
] extends Application
    with AccountGuardian[T, P] { _: SchemaProvider with ApiRoutes with CsrfCheck => }
