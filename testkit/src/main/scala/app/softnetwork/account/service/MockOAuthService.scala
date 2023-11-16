package app.softnetwork.account.service

import app.softnetwork.account.handlers.MockBasicAccountTypeKey
import app.softnetwork.session.service.SessionMaterials

trait MockOAuthService extends BasicOAuthService with MockBasicAccountTypeKey {
  _: SessionMaterials =>
}
