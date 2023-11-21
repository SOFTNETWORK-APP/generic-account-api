package app.softnetwork.account.spi

import com.github.scribejava.apis.FacebookApi
import com.github.scribejava.core.builder.api.DefaultApi20

class FacebookApiService extends OAuth2Service {
  override val networkName: String = "facebook"

  override val instance: DefaultApi20 = FacebookApi.instance()

  override lazy val protectedResourceUrl: String = provider.protectedResourceUrl.getOrElse(
    "https://graph.facebook.com/me?fields=id,name,email"
  )

  override lazy val defaultScope: String = provider.defaultScope.getOrElse("email")
}
