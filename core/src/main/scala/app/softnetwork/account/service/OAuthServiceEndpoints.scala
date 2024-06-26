package app.softnetwork.account.service

import akka.actor.typed.ActorSystem
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.message._
import app.softnetwork.account.model.AuthorizationCode
import app.softnetwork.account.serialization.accountFormats
import app.softnetwork.account.spi.OAuth2Service
import app.softnetwork.api.server.ApiErrors
import app.softnetwork.persistence.generateUUID
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.session.model.{SessionData, SessionDataCompanion, SessionDataDecorator}
import app.softnetwork.session.service.{ServiceWithSessionEndpoints, SessionMaterials}
import com.softwaremill.session.SessionConfig
import org.json4s.Formats
import sttp.capabilities
import sttp.capabilities.akka.AkkaStreams
import sttp.model.headers.AuthenticationScheme.{Basic, Bearer}
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.EndpointIO.Example
import sttp.tapir.json.json4s.jsonBody
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure, Success}

trait OAuthServiceEndpoints[SD <: SessionData with SessionDataDecorator[SD]]
    extends BaseAccountService
    with ServiceWithSessionEndpoints[AccountCommand, AccountCommandResult, SD] {
  _: CommandTypeKey[AccountCommand] with SessionMaterials[SD] =>

  implicit def sessionConfig: SessionConfig

  implicit def companion: SessionDataCompanion[SD]

  override implicit def ts: ActorSystem[_] = system

  import app.softnetwork.serialization.serialization

  override implicit def formats: Formats = accountFormats

  override implicit def resultToApiError(result: AccountCommandResult): ApiErrors.ErrorInfo =
    result match {
      case BasicAuthenticationFailed =>
        ApiErrors.UnauthorizedWithChallenge(Basic, AccountSettings.Realm)
      case BearerAuthenticationFailed =>
        ApiErrors.UnauthorizedWithChallenge(Bearer, AccountSettings.Realm)
      case LoginAndPasswordNotMatched => ApiErrors.Unauthorized(LoginAndPasswordNotMatched)
      case AccountDisabled            => ApiErrors.Unauthorized(AccountDisabled)
      case AccountNotFound            => ApiErrors.NotFound(AccountNotFound)
      case ApplicationNotFound        => ApiErrors.NotFound(ApplicationNotFound)
      case TokenNotFound              => ApiErrors.NotFound(TokenNotFound)
//      case AccessTokenAlreadyExists => ApiErrors.BadRequest(AccessTokenAlreadyExists)
      case r: AccountErrorMessage => ApiErrors.BadRequest(r.message)
      case _                      => ApiErrors.BadRequest("Unknown")
    }

  def challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.basic(AccountSettings.Realm)

  val authorize: ServerEndpoint[Any with AkkaStreams, Future] =
    endpoint.get
      .in(AccountSettings.OAuthPath / "authorize")
      .description("OAuth2 authorize endpoint")
      .securityIn(
        challenge match {
          case WWWAuthenticateChallenge(Basic.name, _) =>
            auth.basic[UsernamePassword](challenge)
          case WWWAuthenticateChallenge(Bearer.name, _) =>
            auth.bearer[UsernamePassword](challenge)
          case WWWAuthenticateChallenge(scheme, _) =>
            auth.http[UsernamePassword](scheme, challenge)
        }
      )
      .errorOut(ApiErrors.oneOfApiErrors)
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
                  r.accessToken.expiresIn,
                  r.accessToken.refreshToken,
                  r.accessToken.refreshExpiresIn
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
                  r.accessToken.expiresIn,
                  r.accessToken.refreshToken,
                  r.accessToken.refreshExpiresIn
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
          .errorOut(ApiErrors.oneOfApiErrors)
          .out(jsonBody[Me])
          .serverSecurityLogicWithOutput(token =>
            run(token, OAuth(token)) map {
              case r: OAuthSucceededResult =>
                val account = r.account
                // create a new session
                var session = companion.newSession
                  .withId(account.uuid)
                  .withAdmin(account.isAdmin)
                  .withAnonymous(false)
                session += ("client_id", r.application.clientId)
                session += ("scope", r.application.accessToken.flatMap(_.scope).getOrElse(""))
                account.currentProfile match {
                  case Some(profile) =>
                    session += (session.profileKey, profile.name)
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

  lazy val services: Seq[OAuth2Service] = OAuth2Service()

  def signin(service: OAuth2Service): ServerEndpoint[Any with AkkaStreams, Future] =
    endpoint.get
      .in(AccountSettings.OAuthPath / service.networkName / "signin")
      .description(s"OAuth2 ${service.networkName} signin endpoint")
      .errorOut(ApiErrors.oneOfApiErrors)
      .serverSecurityLogicWithOutput[Unit, Future](_ =>
        ApiErrors.Found(service.authorizationUrl) match {
          case Some(found) => Future.successful(Left(found))
          case _           => Future.successful(Left(ApiErrors.NotFound()))
        }
      )
      .serverLogic(_ =>
        _ =>
          Future.successful(
            Right(())
          )
      )

  def callback(service: OAuth2Service): ServerEndpoint[Any with AkkaStreams, Future] =
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        endpoint.get
          .in(AccountSettings.OAuthPath / service.networkName / "callback")
          .description(s"OAuth2 ${service.networkName} callback endpoint")
          .securityIn(queryParams.description("authorization query parameters"))
          .errorOut(ApiErrors.oneOfApiErrors)
          .serverSecurityLogicWithOutput(params =>
            params.get("code") match {
              case Some(code) =>
                service.userInfo(code, params.toMap.filterNot(_._1 == "code")) match {
                  case Success(s) =>
                    val data = service.extractData(s)
                    data.login match {
                      case Some(login) =>
                        lookup(login) flatMap {
                          case Some(uuid) =>
                            var session = companion.newSession.withId(uuid).withAnonymous(false)
                            session += ("provider", data.provider)
                            session ++= data.data.toSeq
                            Future.successful(
                              Right((), Some(session))
                            )
                          case _ =>
                            run(generateUUID(), SignUpOAuth(data)) map {
                              case r: AccountCreated =>
                                // create a new session
                                var session =
                                  companion.newSession.withId(r.account.uuid).withAnonymous(false)
                                session += ("provider", data.provider)
                                Right((), Some(session))
                              case other => Left(resultToApiError(other))
                            }
                        }
                      case _ =>
                        log.error(s"login not found within $data for ${service.networkName}")
                        Future.successful(Left(ApiErrors.InternalServerError()))
                    }
                  case Failure(f) =>
                    log.error(f.getMessage, f)
                    Future.successful(Left(ApiErrors.InternalServerError()))
                }
              case _ =>
                Future.successful(Left(ApiErrors.BadRequest()))
            }
          )
      }
    }.serverLogic { _ => _ =>
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
    ) ++ services.map(signin) ++ services.map(callback)

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
