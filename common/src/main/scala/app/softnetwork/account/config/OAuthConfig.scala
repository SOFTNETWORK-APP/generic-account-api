package app.softnetwork.account.config

case class OAuthProvider(
  clientId: Option[String],
  clientSecret: Option[String],
  defaultScope: Option[String],
  protectedResourceUrl: Option[String]
)

case class AuthorizationCode(expirationTime: Int)

case class AccessToken(expirationTime: Int)

case class OAuthConfig(
  path: String,
  authorizationCode: AuthorizationCode,
  accessToken: AccessToken,
  providers: Map[String, OAuthProvider]
)
