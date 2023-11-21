package app.softnetwork.account.spi

import com.github.scribejava.apis.GoogleApi20
import com.github.scribejava.core.builder.api.DefaultApi20

class GoogleApiService extends OAuth2Service {
  override val networkName: String = "google"

  override val instance: DefaultApi20 = GoogleApi20.instance()

  override lazy val protectedResourceUrl: String = provider.protectedResourceUrl.getOrElse(
    "https://www.googleapis.com/oauth2/v1/userinfo?alt=json"
  )

  override lazy val defaultScope: String = provider.defaultScope.getOrElse("openid email profile")
}
