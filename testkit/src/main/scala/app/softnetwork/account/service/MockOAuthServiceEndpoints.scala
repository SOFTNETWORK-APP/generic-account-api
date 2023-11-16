package app.softnetwork.account.service

import app.softnetwork.account.handlers.MockBasicAccountTypeKey
import app.softnetwork.session.service.SessionMaterials

trait MockOAuthServiceEndpoints extends OAuthServiceEndpoints with MockBasicAccountTypeKey {
  _: SessionMaterials =>
}
