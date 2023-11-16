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
import app.softnetwork.session.service.{BasicSessionMaterials, SessionMaterials}
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
    with SessionMaterials
    with ManifestWrapper[DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]] {
  _: ApiRoutes =>

  override protected val manifestWrapper: ManifestW = ManifestW()

  lazy val log: Logger = LoggerFactory getLogger getClass.getName
  override val profile: BasicAccountProfile =
    BasicAccountProfile.defaultInstance
      .withName("name")
      .withType(ProfileType.CUSTOMER)
      .withFirstName(firstName)
      .withLastName(lastName)

}

class OneOfCookieSessionBasicAccountRoutesSpec
    extends BasicAccountRouteSpec
    with OneOfCookieSessionBasicAccountRoutesTestKit
    with BasicSessionMaterials

class OneOfHeaderSessionBasicAccountRoutesSpec
    extends BasicAccountRouteSpec
    with OneOfHeaderSessionBasicAccountRoutesTestKit
    with BasicSessionMaterials

class RefreshableCookieSessionBasicAccountRoutesSpec
    extends BasicAccountRouteSpec
    with RefreshableCookieSessionBasicAccountRoutesTestKit
    with BasicSessionMaterials

class RefreshableHeaderSessionBasicAccountRoutesSpec
    extends BasicAccountRouteSpec
    with RefreshableHeaderSessionBasicAccountRoutesTestKit
    with BasicSessionMaterials

class OneOfCookieSessionBasicAccountEndpointsSpec
    extends BasicAccountRouteSpec
    with OneOfCookieSessionBasicAccountEndpointsTestKit
    with BasicSessionMaterials

class OneOfHeaderSessionBasicAccountEndpointsSpec
    extends BasicAccountRouteSpec
    with OneOfHeaderSessionBasicAccountEndpointsTestKit
    with BasicSessionMaterials

class RefreshableCookieSessionBasicAccountEndpointsSpec
    extends BasicAccountRouteSpec
    with RefreshableCookieSessionBasicAccountEndpointsTestKit
    with BasicSessionMaterials

class RefreshableHeaderSessionBasicAccountEndpointsSpec
    extends BasicAccountRouteSpec
    with RefreshableHeaderSessionBasicAccountEndpointsTestKit
    with BasicSessionMaterials
