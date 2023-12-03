package app.softnetwork.account.service

import app.softnetwork.account.handlers.MockBasicAccountTypeKey
import app.softnetwork.account.spi.OAuth2Service
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import com.github.scribejava.core.oauth.DummyApiService

trait MockOAuthServiceEndpoints[SD <: SessionData with SessionDataDecorator[SD]]
    extends OAuthServiceEndpoints[SD]
    with MockBasicAccountTypeKey {
  _: SessionMaterials[SD] =>
  override lazy val services: Seq[OAuth2Service] =
    Seq(
      new DummyApiService()
    )
}
