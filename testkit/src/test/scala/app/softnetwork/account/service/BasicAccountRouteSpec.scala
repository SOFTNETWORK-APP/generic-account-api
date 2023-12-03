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
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import app.softnetwork.session.model.SessionDataCompanion
import app.softnetwork.session.service.{BasicSessionMaterials, SessionMaterials}
import com.softwaremill.session.RefreshTokenStorage
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

/** Created by smanciot on 22/03/2018.
  */
trait BasicAccountRouteSpec
    extends AccountRouteSpec[
      BasicAccount,
      BasicAccountProfile,
      DefaultProfileView,
      DefaultAccountDetailsView,
      DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView],
      Session
    ]
    with BasicAccountRouteTestKit[Session]
    with SessionMaterials[Session]
    with ManifestWrapper[DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]] {
  _: ApiRoutes =>

  override implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(
    ts
  )

  override implicit def companion: SessionDataCompanion[Session] = Session

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
    with OneOfCookieSessionBasicAccountRoutesTestKit[Session]
    with BasicSessionMaterials[Session]

class OneOfHeaderSessionBasicAccountRoutesSpec
    extends BasicAccountRouteSpec
    with OneOfHeaderSessionBasicAccountRoutesTestKit[Session]
    with BasicSessionMaterials[Session]

class RefreshableCookieSessionBasicAccountRoutesSpec
    extends BasicAccountRouteSpec
    with RefreshableCookieSessionBasicAccountRoutesTestKit[Session]
    with BasicSessionMaterials[Session]

class RefreshableHeaderSessionBasicAccountRoutesSpec
    extends BasicAccountRouteSpec
    with RefreshableHeaderSessionBasicAccountRoutesTestKit[Session]
    with BasicSessionMaterials[Session]

class OneOfCookieSessionBasicAccountEndpointsSpec
    extends BasicAccountRouteSpec
    with OneOfCookieSessionBasicAccountEndpointsTestKit[Session]
    with BasicSessionMaterials[Session]

class OneOfHeaderSessionBasicAccountEndpointsSpec
    extends BasicAccountRouteSpec
    with OneOfHeaderSessionBasicAccountEndpointsTestKit[Session]
    with BasicSessionMaterials[Session]

class RefreshableCookieSessionBasicAccountEndpointsSpec
    extends BasicAccountRouteSpec
    with RefreshableCookieSessionBasicAccountEndpointsTestKit[Session]
    with BasicSessionMaterials[Session]

class RefreshableHeaderSessionBasicAccountEndpointsSpec
    extends BasicAccountRouteSpec
    with RefreshableHeaderSessionBasicAccountEndpointsTestKit[Session]
    with BasicSessionMaterials[Session]
