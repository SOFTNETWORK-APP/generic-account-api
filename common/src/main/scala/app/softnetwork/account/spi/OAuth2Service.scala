package app.softnetwork.account.spi

import app.softnetwork.account.config.{AccountSettings, OAuthProvider}
import app.softnetwork.account.model.OAuthData
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.model.{OAuth2AccessToken, OAuthRequest, Response}
import com.github.scribejava.core.oauth.{AccessTokenRequestParams, OAuth20Service}
import com.typesafe.scalalogging.StrictLogging

import scala.util.Try

trait OAuth2Service extends StrictLogging {
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
    import scala.collection.JavaConverters._
    service.getAccessToken(AccessTokenRequestParams.create(code).addExtraParameters(extraParameters.asJava))
  }

  def refreshAccessToken(refreshToken: String): OAuth2AccessToken =
    service.refreshAccessToken(refreshToken)

  def authorizationUrl: String = service.getAuthorizationUrl()

  val jsonMapper: ObjectMapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .setSerializationInclusion(Include.NON_EMPTY)
  jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def userInfo(code: String, extraParameters: Map[String, String]): Try[Map[String, String]] = Try {
    val accessToken = getAccessToken(code, extraParameters)
    import com.github.scribejava.core.model.{OAuthRequest, Verb}
    val request =
      new OAuthRequest(Verb.GET, protectedResourceUrl)
    signRequest(accessToken, request)
    jsonMapper
      .readValue(execute(request).getBody, classOf[Map[String, Any]])
      .mapValues(_.toString)
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
