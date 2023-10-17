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
import app.softnetwork.api.server.ApiRoute
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck

trait BasicAccountRoutes
    extends AccountRoutes[
      BasicAccount,
      BasicAccountProfile,
      DefaultProfileView,
      DefaultAccountDetailsView,
      DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]
    ] {
  _: BasicAccountApi with SchemaProvider with CsrfCheck =>
  override def accountService: ActorSystem[_] => AccountService[
    DefaultProfileView,
    DefaultAccountDetailsView,
    DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]
  ] =
    sys => BasicAccountService(sys, sessionService(sys))

  override def apiRoutes: ActorSystem[_] => List[ApiRoute] = system =>
    super.apiRoutes(system) :+ accountSwagger(system)
}
