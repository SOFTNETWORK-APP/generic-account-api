package app.softnetwork.account.service

import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile, ProfileType}
import app.softnetwork.account.scalatest.{
  AccountRouteSpec,
  BasicAccountRouteTestKit,
  BasicAccountRoutesTestKit,
  OneOfCookieSessionBasicAccountRoutesTestKit,
  OneOfHeaderSessionBasicAccountRoutesTestKit
}
import org.slf4j.{Logger, LoggerFactory}

/** Created by smanciot on 22/03/2018.
  */
trait BasicAccountRouteSpec
    extends AccountRouteSpec[BasicAccount, BasicAccountProfile]
    with BasicAccountRouteTestKit { _: BasicAccountRoutesTestKit =>

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
