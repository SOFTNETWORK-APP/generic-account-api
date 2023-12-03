package app.softnetwork.account.service

import app.softnetwork.account.handlers.MockBasicAccountTypeKey
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials

trait MockBasicAccountService[SD <: SessionData with SessionDataDecorator[SD]]
    extends BasicAccountService[SD]
    with MockBasicAccountTypeKey {
  _: SessionMaterials[SD] =>
}
