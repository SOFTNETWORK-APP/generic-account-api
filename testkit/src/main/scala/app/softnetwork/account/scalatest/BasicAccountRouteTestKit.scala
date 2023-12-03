package app.softnetwork.account.scalatest

import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import org.scalatest.Suite

trait BasicAccountRouteTestKit[SD <: SessionData with SessionDataDecorator[SD]]
    extends BasicAccountTestKit
    with AccountRouteTestKit[BasicAccount, BasicAccountProfile, SD] {
  _: Suite with ApiRoutes with SessionMaterials[SD] =>

}
