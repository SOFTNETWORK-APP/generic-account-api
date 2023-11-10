package app.softnetwork.account.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.account.model.{
  BasicAccount,
  BasicAccountProfile,
  DefaultAccountDetailsView,
  DefaultAccountView,
  DefaultProfileView
}
import app.softnetwork.account.service.{
  AccountService,
  MockBasicAccountService,
  MockOAuthService,
  OAuthService
}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.scalatest.{
  OneOffCookieSessionServiceTestKit,
  OneOffHeaderSessionServiceTestKit,
  RefreshableCookieSessionServiceTestKit,
  RefreshableHeaderSessionServiceTestKit
}
import org.scalatest.Suite

trait BasicAccountRoutesTestKit
    extends AccountRoutesTestKit[
      BasicAccount,
      BasicAccountProfile,
      DefaultProfileView,
      DefaultAccountDetailsView,
      DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]
    ] {
  _: SchemaProvider with CsrfCheck =>

  override def accountService: ActorSystem[_] => AccountService[
    DefaultProfileView,
    DefaultAccountDetailsView,
    DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]
  ] = system => MockBasicAccountService(system, sessionService(system))

  override def oauthService: ActorSystem[_] => OAuthService = system =>
    MockOAuthService(system, sessionService(system))
}

trait OneOfCookieSessionBasicAccountRoutesTestKit
    extends OneOffCookieSessionServiceTestKit
    with BasicAccountRoutesTestKit { _: Suite => }

trait OneOfHeaderSessionBasicAccountRoutesTestKit
    extends OneOffHeaderSessionServiceTestKit
    with BasicAccountRoutesTestKit { _: Suite => }

trait RefreshableCookieSessionBasicAccountRoutesTestKit
    extends RefreshableCookieSessionServiceTestKit
    with BasicAccountRoutesTestKit { _: Suite => }

trait RefreshableHeaderSessionBasicAccountRoutesTestKit
    extends RefreshableHeaderSessionServiceTestKit
    with BasicAccountRoutesTestKit { _: Suite => }
