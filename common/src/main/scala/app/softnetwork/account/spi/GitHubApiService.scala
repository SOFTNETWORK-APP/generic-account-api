package app.softnetwork.account.spi

import com.github.scribejava.apis.GitHubApi
import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.model.OAuth2AccessToken

import scala.util.Try

class GitHubApiService extends OAuth2Service {
  override val networkName: String = "github"

  override val instance: DefaultApi20 = GitHubApi.instance()

  override lazy val protectedResourceUrl: String = provider.protectedResourceUrl.getOrElse(
    "https://api.github.com/user"
  )

  override lazy val defaultScope: String =
    provider.defaultScope.getOrElse("user:email") // user includes email

  /** GitHub returns `email: null` from /user when the primary email is private. The `user:email`
    * scope lets us read /user/emails; resolve the primary verified address there and inject it so
    * account login resolution (which keys on email) succeeds for users who keep their email
    * private.
    */
  protected def emailsUrl: String = "https://api.github.com/user/emails"

  override def userInfo(
    code: String,
    extraParameters: Map[String, String]
  ): Try[Map[String, String]] = Try {
    val accessToken = getAccessToken(code, extraParameters)
    val user = fetch(accessToken, protectedResourceUrl)
    if (user.get("email").exists(_.nonEmpty)) user
    else primaryEmail(accessToken).fold(user)(email => user + ("email" -> email))
  }

  /** Returns the primary verified email from GitHub's /user/emails, falling back to any verified
    * address, then the first one listed.
    */
  protected def primaryEmail(accessToken: OAuth2AccessToken): Option[String] = {
    import scala.jdk.CollectionConverters._
    val node = jsonMapper.readTree(get(accessToken, emailsUrl))
    if (!node.isArray) None
    else {
      val entries = node.elements().asScala.toList
      entries
        .find(e => e.path("primary").asBoolean(false) && e.path("verified").asBoolean(false))
        .orElse(entries.find(_.path("verified").asBoolean(false)))
        .orElse(entries.headOption)
        .map(_.path("email").asText(""))
        .filter(_.nonEmpty)
    }
  }
}
