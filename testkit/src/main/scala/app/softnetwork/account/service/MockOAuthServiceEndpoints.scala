package app.softnetwork.account.service

import app.softnetwork.account.handlers.MockBasicAccountTypeKey
import app.softnetwork.account.spi.OAuth2Service
import app.softnetwork.session.service.SessionMaterials
import com.github.scribejava.core.oauth.DummyApiService

trait MockOAuthServiceEndpoints extends OAuthServiceEndpoints with MockBasicAccountTypeKey {
  _: SessionMaterials =>
  override lazy val services: Seq[OAuth2Service] =
    Seq(
      new DummyApiService()
    )
}
