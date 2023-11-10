package app.softnetwork.account.service

import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.message._
import app.softnetwork.account.model.AuthorizationCode
import app.softnetwork.account.serialization.accountFormats
import app.softnetwork.api.server.ApiErrors
import app.softnetwork.api.server.ApiErrors.ExtendedErrorInfo
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.session.service.ServiceWithSessionEndpoints
import org.json4s.Formats
import org.softnetwork.session.model.Session
import org.softnetwork.session.model.Session.profileKey
import sttp.capabilities
import sttp.capabilities.akka.AkkaStreams
import sttp.model.headers.AuthenticationScheme.{Basic, Bearer}
import sttp.model.{HeaderNames, StatusCode}
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.EndpointIO.Example
import sttp.tapir.{Codec, CodecFormat, DecodeResult, EndpointOutput}
import sttp.tapir.json.json4s.jsonBody
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future
import scala.language.implicitConversions

trait OAuthServiceEndpoints
    extends BaseAccountService
    with ServiceWithSessionEndpoints[AccountCommand, AccountCommandResult] {
  _: CommandTypeKey[AccountCommand] =>

  import app.softnetwork.serialization.serialization

  override implicit def formats: Formats = accountFormats

  implicit val unauthorizedWithChallengeCodec
    : Codec[String, UnauthorizedWithChallenge, CodecFormat.TextPlain] =
    Codec.string.mapDecode(s =>
      WWWAuthenticateChallenge.parseSingle(s) match {
        case Right(challenge) =>
          DecodeResult.Value(
            UnauthorizedWithChallenge(challenge.scheme, challenge.realm.getOrElse(""))
          )
        case Left(_) => DecodeResult.Error(s, new Exception("Cannot parse WWW-Authenticate header"))
      }
    )(_.toString())

  val unauthorizedWithChallengeVariant: EndpointOutput.OneOfVariant[UnauthorizedWithChallenge] =
    oneOfVariant(
      statusCode(StatusCode.Unauthorized)
        .and(header[UnauthorizedWithChallenge](HeaderNames.WwwAuthenticate))
    )

  override implicit def resultToApiError(result: AccountCommandResult): ApiErrors.ErrorInfo =
    result match {
      case BasicAuthenticationFailed  => UnauthorizedWithChallenge(Basic, AccountSettings.Realm)
      case BearerAuthenticationFailed => UnauthorizedWithChallenge(Bearer, AccountSettings.Realm)
      case LoginAndPasswordNotMatched => ApiErrors.Unauthorized(LoginAndPasswordNotMatched)
      case AccountDisabled            => ApiErrors.Unauthorized(AccountDisabled)
      case AccountNotFound            => ApiErrors.NotFound(AccountNotFound)
      case ApplicationNotFound        => ApiErrors.NotFound(ApplicationNotFound)
      case TokenNotFound              => ApiErrors.NotFound(TokenNotFound)
//      case AccessTokenAlreadyExists => ApiErrors.BadRequest(AccessTokenAlreadyExists)
      case r: AccountErrorMessage => ApiErrors.BadRequest(r.message)
      case _                      => ApiErrors.BadRequest("Unknown")
    }

  val authorize: ServerEndpoint[Any with AkkaStreams, Future] =
    endpoint.get
      .in(AccountSettings.OAuthPath / "authorize")
      .description("OAuth2 authorize endpoint")
      .securityIn(
        auth.basic[UsernamePassword](WWWAuthenticateChallenge.basic(AccountSettings.Realm))
      )
      .errorOut(
        oneOf[ApiErrors.ErrorInfo](
          // returns required http code for different types of ErrorInfo.
          // For secured endpoint you need to define
          // all cases before defining security logic
          ApiErrors.forbiddenVariant,
          ApiErrors.unauthorizedVariant,
          unauthorizedWithChallengeVariant,
          ApiErrors.notFoundVariant,
          ApiErrors.foundVariant,
          ApiErrors.badRequestVariant,
          ApiErrors.internalServerErrorVariant,
          // default case below.
          ApiErrors.defaultErrorVariant
        )
      )
      .serverSecurityLogicWithOutput(up =>
        run(up.username, Login(up.username, up.password.getOrElse(""))) map {
          case r: LoginSucceededResult =>
            val account = r.account
            Right((), account.uuid)
          case _ => Left(resultToApiError(BasicAuthenticationFailed))
        }
      )
      .in(
        query[String]("response_type").description("response type, currently only code is accepted")
      )
      .in(query[String]("client_id").description("client id"))
      .in(query[Option[String]]("redirect_uri").description("redirect uri"))
      .in(query[Option[String]]("scope").description("scope"))
      .in(query[Option[String]]("state").description("state"))
      .out(jsonBody[AuthorizationCode])
      .serverLogic(uuid => { case (responseType, clientId, redirectUri, scope, state) =>
        if (responseType != "code") {
          Future.successful(
            Left(
              (redirectUri match {
                case Some(uri) =>
                  ApiErrors.Found(
                    redirection(
                      uri,
                      Map("error" -> "unsupported_response_type", "state" -> state.getOrElse(""))
                    )
                  )
                case _ => None
              }).getOrElse(ApiErrors.BadRequest("unsupported_response_type"))
            )
          )
        } else {
          run(uuid, GenerateAuthorizationCode(clientId, scope, redirectUri, state)) map {
            case r: AuthorizationCodeGenerated =>
              (redirectUri match {
                case Some(uri) =>
                  ApiErrors.Found(
                    redirection(
                      uri,
                      Map("code" -> r.authorizationCode.code, "state" -> state.getOrElse(""))
                    )
                  )
                case _ => None
              }) match {
                case Some(found) => Left(found)
                case _           => Right(r.authorizationCode)
              }
            case other => Left(resultToApiError(other))
          }
        }
      })

  val token: ServerEndpoint[Any with AkkaStreams, Future] =
    endpoint.post
      .in(AccountSettings.OAuthPath / "token")
      .description("OAuth2 token endpoint")
      .errorOut(ApiErrors.oneOfApiErrors)
      .in(
        formBody[Map[String, String]]
          .description("form body")
          .map[TokenRequest](m => TokenRequest.decode(m))(TokenRequest.encode)
          .example(
            Example.of(
              AuthorizationCodeRequest(
                "authorization_code",
                "SplxlOBeZQQYbYS6WxSbIA",
                Some("https://client/example/com/cb"),
                "client"
              ),
              Some("authorization code request")
            )
          )
          .example(
            Example.of(
              RefreshTokenRequest("refresh_token", "tGzv3JOkF0XG5Qx2TlKWIA"),
              Some("refresh token request")
            )
          )
      )
      .out(jsonBody[Tokens])
      .serverLogic {
        case tokenRequest: AuthorizationCodeRequest =>
          import tokenRequest._
          run(code, GenerateAccessToken(client_id, code, redirect_uri)) map {
            case r: AccessTokenGenerated =>
              Right(
                Tokens(
                  r.accessToken.token,
                  r.accessToken.tokenType.toLowerCase(),
                  AccountSettings.AccessTokenExpirationTime * 60,
                  r.accessToken.refreshToken
                )
              )
            case error: AccountErrorMessage =>
              Left(ApiErrors.BadRequest(error.message))
            case _ => Left(ApiErrors.BadRequest("Unknown"))
          }
        case tokenRequest: RefreshTokenRequest =>
          import tokenRequest._
          run(refresh_token, RefreshAccessToken(refresh_token)) map {
            case r: AccessTokenRefreshed =>
              Right(
                Tokens(
                  r.accessToken.token,
                  r.accessToken.tokenType.toLowerCase(),
                  AccountSettings.AccessTokenExpirationTime * 60,
                  r.accessToken.refreshToken
                )
              )
            case error: AccountErrorMessage =>
              Left(ApiErrors.BadRequest(error.message))
            case _ => Left(ApiErrors.BadRequest("Unknown"))
          }
        case tokenRequest: UnsupportedGrantType =>
          Future.successful(
            Left(ApiErrors.BadRequest(s"Unknown grant_type ${tokenRequest.grant_type}"))
          )
      }

  val me: ServerEndpoint[Any with AkkaStreams, Future] =
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        endpoint.get
          .in(AccountSettings.OAuthPath / "me")
          .description("OAuth2 me endpoint")
          .securityIn(auth.bearer[String](WWWAuthenticateChallenge.bearer(AccountSettings.Realm)))
          .errorOut(
            oneOf[ApiErrors.ErrorInfo](
              unauthorizedWithChallengeVariant
            )
          )
          .out(jsonBody[Me])
          .serverSecurityLogicWithOutput(token =>
            run(token, OAuth(token)) map {
              case r: LoginSucceededResult =>
                val account = r.account
                // create a new session
                val session = Session(account.uuid)
                session += (Session.adminKey, account.isAdmin)
                session += (Session.anonymousKey, false)
                account.currentProfile match {
                  case Some(profile) =>
                    session += (profileKey, profile.name)
                  case _ =>
                }
                Right(
                  account.details
                    .map(d => Me(d.firstName, d.lastName, d.email))
                    .getOrElse(Me("", "")),
                  Some(session)
                )

              case _ => Left(resultToApiError(BearerAuthenticationFailed))
            }
          )
      }
    }
      .serverLogic { _ => _ =>
        Future.successful(
          Right(())
        )
      }

  override lazy val endpoints
    : List[ServerEndpoint[AkkaStreams with capabilities.WebSockets, Future]] =
    List(
      authorize,
      token,
      me
    )

}

sealed trait TokenRequest {
  def asMap(): Map[String, String]
}

case class AuthorizationCodeRequest(
  grant_type: String,
  code: String,
  redirect_uri: Option[String],
  client_id: String
) extends TokenRequest {
  override def asMap(): Map[String, String] =
    Map(
      "grant_type"   -> grant_type,
      "code"         -> code,
      "redirect_uri" -> redirect_uri.getOrElse(""),
      "client_id"    -> client_id
    )
}

case class RefreshTokenRequest(grant_type: String, refresh_token: String) extends TokenRequest {
  override def asMap(): Map[String, String] =
    Map(
      "grant_type"    -> grant_type,
      "refresh_token" -> refresh_token
    )
}

case class UnsupportedGrantType(grant_type: String) extends TokenRequest {
  override def asMap(): Map[String, String] =
    Map(
      "grant_type" -> grant_type
    )
}

object TokenRequest {
  def decode(form: Map[String, String]): TokenRequest =
    form.getOrElse("grant_type", "") match {
      case "authorization_code" =>
        AuthorizationCodeRequest(
          "authorization_code",
          form.getOrElse("code", ""),
          form.get("redirect_uri"),
          form.getOrElse("client_id", "")
        )
      case "refresh_token" =>
        RefreshTokenRequest("refresh_token", form.getOrElse("refresh_token", ""))
      case other => UnsupportedGrantType(other)
    }

  def encode(tokenRequest: TokenRequest): Map[String, String] = tokenRequest.asMap()
}

case class UnauthorizedWithChallenge(scheme: String, realm: String) extends ExtendedErrorInfo {
  override val message: String = "Unauthorized"

  override def toString: String = WWWAuthenticateChallenge(scheme).realm(realm).toString()
}
