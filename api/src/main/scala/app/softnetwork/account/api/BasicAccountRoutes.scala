package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.account.handlers.BasicAccountTypeKey
import app.softnetwork.account.launch.AccountRoutes
import app.softnetwork.account.model.{
  BasicAccount,
  BasicAccountProfile,
  DefaultAccountDetailsView,
  DefaultAccountView,
  DefaultProfileView
}
import app.softnetwork.account.service.{
  AccountService,
  BasicAccountService,
  BasicOAuthService,
  OAuthService
}
import app.softnetwork.api.server.ApiRoute
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.service.SessionService
import org.slf4j.{Logger, LoggerFactory}

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

  override def oauthService: ActorSystem[_] => OAuthService =
    sys =>
      new BasicOAuthService {
        override def service: SessionService = sessionService(sys)
        override implicit def system: ActorSystem[_] = sys
        override def log: Logger = LoggerFactory getLogger getClass.getName
      }

  override def apiRoutes: ActorSystem[_] => List[ApiRoute] = system =>
    super.apiRoutes(system) :+ accountSwagger(system) :+ oauthSwagger(system)
}
