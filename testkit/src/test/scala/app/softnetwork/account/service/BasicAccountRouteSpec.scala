package app.softnetwork.account.service

import app.softnetwork.account.model.{
  BasicAccount,
  BasicAccountProfile,
  DefaultAccountDetailsView,
  DefaultAccountView,
  DefaultProfileView,
  ProfileType
}
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
import app.softnetwork.persistence.ManifestWrapper
import org.slf4j.{Logger, LoggerFactory}

/** Created by smanciot on 22/03/2018.
  */
trait BasicAccountRouteSpec
    extends AccountRouteSpec[
      BasicAccount,
      BasicAccountProfile,
      DefaultProfileView,
      DefaultAccountDetailsView,
      DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]
    ]
    with BasicAccountRouteTestKit
    with ManifestWrapper[DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]] {
  _: ApiRoutes =>

  override protected val manifestWrapper: ManifestW = ManifestW()

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
