package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.account.launch.AccountRoutes
import app.softnetwork.account.model.{
  BasicAccount,
  BasicAccountProfile,
  DefaultAccountDetailsView,
  DefaultAccountView,
  DefaultProfileView
}
import app.softnetwork.account.service.{AccountService, BasicAccountService}
import app.softnetwork.persistence.schema.SchemaProvider

trait BasicAccountRoutes
    extends AccountRoutes[
      BasicAccount,
      BasicAccountProfile,
      DefaultProfileView,
      DefaultAccountDetailsView,
      DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]
    ] {
  _: SchemaProvider =>
  override def accountService: ActorSystem[_] => AccountService[
    DefaultProfileView,
    DefaultAccountDetailsView,
    DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]
  ] =
    sys => BasicAccountService(sys, sessionService(sys))
}
