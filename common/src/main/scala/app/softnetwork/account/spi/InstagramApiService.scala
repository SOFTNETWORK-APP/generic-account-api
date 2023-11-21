package app.softnetwork.account.spi

import com.github.scribejava.apis.InstagramApi
import com.github.scribejava.core.builder.api.DefaultApi20

class InstagramApiService extends OAuth2Service {
  override val networkName: String = "instagram"

  override val instance: DefaultApi20 = InstagramApi.instance()

  override lazy val protectedResourceUrl: String = provider.protectedResourceUrl.getOrElse(
    "https://graph.instagram.com/me?fields=id,username"
  )

  override lazy val defaultScope: String = provider.defaultScope.getOrElse("user_profile")
}
