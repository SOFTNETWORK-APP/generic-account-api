package app.softnetwork.account.service

import app.softnetwork.account.handlers.MockBasicAccountTypeKey
import app.softnetwork.session.service.SessionMaterials

trait MockBasicAccountService extends BasicAccountService with MockBasicAccountTypeKey {
  _: SessionMaterials =>
}
