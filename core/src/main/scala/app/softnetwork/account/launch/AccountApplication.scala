package app.softnetwork.account.launch

import app.softnetwork.api.server.launch.Application
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}

/** Created by smanciot on 22/03/2018.
  */
trait AccountApplication[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator
] extends Application
    with AccountRoutes[T, P]
