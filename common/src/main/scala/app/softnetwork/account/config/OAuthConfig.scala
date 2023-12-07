package app.softnetwork.account.config

case class OAuthProvider(
  clientId: Option[String],
  clientSecret: Option[String],
  defaultScope: Option[String],
  protectedResourceUrl: Option[String]
)

case class AuthorizationCode(expirationTime: Int)

sealed trait Token {
  def expirationTime: Int
}

case class AccessToken(expirationTime: Int) extends Token

case class RefreshToken(expirationTime: Int) extends Token

case class OAuthConfig(
  path: String,
  authorizationCode: AuthorizationCode,
  accessToken: AccessToken,
  refreshToken: RefreshToken,
  providers: Map[String, OAuthProvider]
)
