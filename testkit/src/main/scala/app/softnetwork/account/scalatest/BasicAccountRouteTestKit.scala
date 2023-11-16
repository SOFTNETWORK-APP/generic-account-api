package app.softnetwork.account.scalatest

import app.softnetwork.account.message.{BasicAccountSignUp, SignUp}
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.service.SessionMaterials
import org.scalatest.Suite

trait BasicAccountRouteTestKit
    extends BasicAccountTestKit
    with AccountRouteTestKit[BasicAccount, BasicAccountProfile] {
  _: Suite with ApiRoutes with SessionMaterials =>

}
