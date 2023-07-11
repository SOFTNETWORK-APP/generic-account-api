package app.softnetwork.account.service

import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile, ProfileType}
import app.softnetwork.account.scalatest.{
  AccountRouteSpec,
  BasicAccountRouteTestKit,
  OneOfCookieSessionBasicAccountEndpointsTestKit,
  OneOfCookieSessionBasicAccountRoutesTestKit,
  OneOfHeaderSessionBasicAccountEndpointsTestKit,
  OneOfHeaderSessionBasicAccountRoutesTestKit,
  RefreshableCookieSessionBasicAccountEndpointsTestKit,
  RefreshableCookieSessionBasicAccountRoutesTestKit,
  RefreshableHeaderSessionBasicAccountEndpointsTestKit,
  RefreshableHeaderSessionBasicAccountRoutesTestKit
}
import app.softnetwork.api.server.ApiRoutes
import org.slf4j.{Logger, LoggerFactory}

/** Created by smanciot on 22/03/2018.
  */
trait BasicAccountRouteSpec
    extends AccountRouteSpec[BasicAccount, BasicAccountProfile]
    with BasicAccountRouteTestKit { _: ApiRoutes =>

  lazy val log: Logger = LoggerFactory getLogger getClass.getName
  override val profile: BasicAccountProfile =
    BasicAccountProfile.defaultInstance
      .withName("name")
      .withType(ProfileType.CUSTOMER)

}

class OneOfCookieSessionBasicAccountRoutesSpec
    extends BasicAccountRouteSpec
    with OneOfCookieSessionBasicAccountRoutesTestKit {}

class OneOfHeaderSessionBasicAccountRoutesSpec
    extends BasicAccountRouteSpec
    with OneOfHeaderSessionBasicAccountRoutesTestKit {}

class RefreshableCookieSessionBasicAccountRoutesSpec
    extends BasicAccountRouteSpec
    with RefreshableCookieSessionBasicAccountRoutesTestKit {}

class RefreshableHeaderSessionBasicAccountRoutesSpec
    extends BasicAccountRouteSpec
    with RefreshableHeaderSessionBasicAccountRoutesTestKit {}

class OneOfCookieSessionBasicAccountEndpointsSpec
    extends BasicAccountRouteSpec
    with OneOfCookieSessionBasicAccountEndpointsTestKit {}

class OneOfHeaderSessionBasicAccountEndpointsSpec
    extends BasicAccountRouteSpec
    with OneOfHeaderSessionBasicAccountEndpointsTestKit {}

class RefreshableCookieSessionBasicAccountEndpointsSpec
    extends BasicAccountRouteSpec
    with RefreshableCookieSessionBasicAccountEndpointsTestKit {}

class RefreshableHeaderSessionBasicAccountEndpointsSpec
    extends BasicAccountRouteSpec
    with RefreshableHeaderSessionBasicAccountEndpointsTestKit {}
