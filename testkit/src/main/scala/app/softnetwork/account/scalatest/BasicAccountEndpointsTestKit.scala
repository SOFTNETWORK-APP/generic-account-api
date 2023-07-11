package app.softnetwork.account.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.account.message.BasicAccountSignUp
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.service.{AccountServiceEndpoints, MockBasicAccountServiceEndpoints}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.scalatest.{
  OneOffCookieSessionEndpointsTestKit,
  OneOffHeaderSessionEndpointsTestKit,
  RefreshableCookieSessionEndpointsTestKit,
  RefreshableHeaderSessionEndpointsTestKit
}
import com.softwaremill.session.CsrfCheckHeader
import org.scalatest.Suite

trait BasicAccountEndpointsTestKit
    extends AccountEndpointsTestKit[BasicAccount, BasicAccountProfile, BasicAccountSignUp] {
  _: SchemaProvider =>
  override def accountEndpoints: ActorSystem[_] => AccountServiceEndpoints[BasicAccountSignUp] =
    system => MockBasicAccountServiceEndpoints(system, sessionEndpoints(system))
}

trait OneOfCookieSessionBasicAccountEndpointsTestKit
    extends OneOffCookieSessionEndpointsTestKit
    with BasicAccountEndpointsTestKit
    with CsrfCheckHeader { _: Suite => }

trait OneOfHeaderSessionBasicAccountEndpointsTestKit
    extends OneOffHeaderSessionEndpointsTestKit
    with BasicAccountEndpointsTestKit
    with CsrfCheckHeader { _: Suite => }

trait RefreshableCookieSessionBasicAccountEndpointsTestKit
    extends RefreshableCookieSessionEndpointsTestKit
    with BasicAccountEndpointsTestKit
    with CsrfCheckHeader { _: Suite => }

trait RefreshableHeaderSessionBasicAccountEndpointsTestKit
    extends RefreshableHeaderSessionEndpointsTestKit
    with BasicAccountEndpointsTestKit
    with CsrfCheckHeader { _: Suite => }