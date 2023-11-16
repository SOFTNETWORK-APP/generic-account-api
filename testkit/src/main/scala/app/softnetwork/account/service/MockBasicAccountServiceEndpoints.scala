package app.softnetwork.account.service

import app.softnetwork.account.handlers.MockBasicAccountTypeKey
import app.softnetwork.session.service.SessionMaterials

trait MockBasicAccountServiceEndpoints
    extends BasicAccountServiceEndpoints
    with MockBasicAccountTypeKey { _: SessionMaterials => }
