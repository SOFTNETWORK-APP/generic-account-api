package app.softnetwork.account.spi

import com.github.scribejava.apis.GitHubApi
import com.github.scribejava.core.builder.api.DefaultApi20

class GitHubApiService extends OAuth2Service {
  override val networkName: String = "github"

  override val instance: DefaultApi20 = GitHubApi.instance()

  override lazy val protectedResourceUrl: String = provider.protectedResourceUrl.getOrElse(
    "https://api.github.com/user"
  )

  override lazy val defaultScope: String =
    provider.defaultScope.getOrElse("user:email") // user includes email
}
