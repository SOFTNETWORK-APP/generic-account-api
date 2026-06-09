package app.softnetwork.account.spi

import app.softnetwork.account.config.{AccountSettings, OAuthProvider}
import app.softnetwork.account.model.OAuthData
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.model.{OAuth2AccessToken, OAuthRequest, Response, Verb}
import com.github.scribejava.core.oauth.{AccessTokenRequestParams, OAuth20Service}

import scala.util.Try

trait OAuth2Service {
  def networkName: String

  protected def provider: OAuthProvider = AccountSettings.OAuthSettings
    .providers(networkName)

  def protectedResourceUrl: String = provider.protectedResourceUrl
    .getOrElse(throw new Exception(s"$networkName protectedResourceUrl not defined"))

  def callbackUrl: String =
    s"${AccountSettings.BaseUrl}/${AccountSettings.OAuthPath}/$networkName/callback"

  def clientId: String = provider.clientId
    .getOrElse(throw new Exception(s"$networkName clientId not defined"))

  def clientSecret: String = provider.clientSecret
    .getOrElse(throw new Exception(s"$networkName clientSecret not defined"))

  def defaultScope: String = provider.defaultScope
    .getOrElse(throw new Exception(s"$networkName defaultScope not defined"))

  def instance: DefaultApi20

  lazy val service: OAuth20Service = new ServiceBuilder(clientId)
    .apiSecret(clientSecret)
    .defaultScope(defaultScope)
    .callback(callbackUrl)
    .build(instance)

  def execute(request: OAuthRequest): Response = service.execute(request)

  def signRequest(accessToken: OAuth2AccessToken, request: OAuthRequest): Unit =
    service.signRequest(accessToken, request)

  def getAccessToken(code: String, extraParameters: Map[String, String]): OAuth2AccessToken = {
    import scala.jdk.CollectionConverters._
    service.getAccessToken(
      AccessTokenRequestParams.create(code).setExtraParameters(extraParameters.asJava)
    )
  }

  def refreshAccessToken(refreshToken: String): OAuth2AccessToken =
    service.refreshAccessToken(refreshToken)

  def authorizationUrl: String = service.getAuthorizationUrl()

  val jsonMapper: ObjectMapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .setSerializationInclusion(Include.NON_EMPTY)
  jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  /** Executes a signed GET against `url` and returns the raw response body. */
  protected def get(accessToken: OAuth2AccessToken, url: String): String = {
    val request = new OAuthRequest(Verb.GET, url)
    signRequest(accessToken, request)
    execute(request).getBody
  }

  /** Fetches `url` as a JSON object and flattens it to a `Map[String, String]`. Providers (notably
    * GitHub's /user) return many nullable fields; calling toString on a null value throws NPE, so
    * null entries are dropped first.
    */
  protected def fetch(accessToken: OAuth2AccessToken, url: String): Map[String, String] =
    jsonMapper
      .readValue(get(accessToken, url), classOf[Map[String, Any]])
      .filter { case (_, value) => value != null }
      .mapValues(_.toString)
      .toMap

  def userInfo(code: String, extraParameters: Map[String, String]): Try[Map[String, String]] = Try {
    fetch(getAccessToken(code, extraParameters), protectedResourceUrl)
  }

  def extractData(data: Map[String, String]): OAuthData = OAuthData(networkName, data)
}

trait OAuth2ServiceCompanion {
  def apply(networkName: String): OAuth2Service = networkName match {
    case "google"    => new GoogleApiService()
    case "facebook"  => new FacebookApiService()
    case "github"    => new GitHubApiService()
    case "instagram" => new InstagramApiService()
    case _           => throw new Exception(s"OAuth2Service for $networkName not defined")
  }

  def apply(): Seq[OAuth2Service] = AccountSettings.OAuthSettings.providers.keys
    .map(apply)
    .toSeq
}

object OAuth2Service extends OAuth2ServiceCompanion
