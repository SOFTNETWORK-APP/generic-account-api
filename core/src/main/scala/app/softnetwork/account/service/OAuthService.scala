package app.softnetwork.account.service

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{AuthenticationFailedRejection, RejectionHandler, Route}
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.message._
import app.softnetwork.account.serialization._
import app.softnetwork.api.server._
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.session.service.SessionService
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{jackson, Formats}
import org.json4s.jackson.Serialization
import org.softnetwork.session.model.Session
import Session._
import app.softnetwork.account.handlers.BasicAccountTypeKey

trait OAuthService
    extends AkkaAccountService
    with DefaultComplete
    with Json4sSupport
    with StrictLogging
    with ApiRoute { _: CommandTypeKey[AccountCommand] =>

  def service: SessionService

  implicit def formats: Formats = accountFormats

  implicit def serialization: Serialization.type = jackson.Serialization

  override val route: Route = {
    pathPrefix(AccountSettings.OAuthPath) {
      authorize ~ token ~ me
    }
  }

  lazy val authorize: Route =
    path("authorize") {
      get {
        parameter("response_type") {
          case "code" =>
            parameters("client_id", "redirect_uri".?, "scope".?, "state".?) {
              (clientId, redirectUri, scope, state) =>
                service.optionalSession {
                  case Some(session) =>
                    hmacTokenCsrfProtection(checkHeader) {
                      generateCode(session.id, clientId, scope, redirectUri, state)
                    }
                  case _ => complete(StatusCodes.Unauthorized)
                }
                handleRejections(
                  RejectionHandler
                    .newBuilder()
                    .handleAll[AuthenticationFailedRejection](authenticationFailedRejectionHandler)
                    .result()
                ) {
                  authenticateBasicAsync(AccountSettings.Realm, basicAuth) { account =>
                    generateCode(account.uuid, clientId, scope, redirectUri, state)
                  }
                }
            }
          case _ =>
            parameters("redirect_uri".?, "state".?) { (redirectUri, state) =>
              redirectUri match {
                case Some(uri) =>
                  redirect(
                    redirection(
                      uri,
                      Map("error" -> "unsupported_response_type", "state" -> state.getOrElse(""))
                    ),
                    StatusCodes.Found
                  )
                case _ => complete(StatusCodes.BadRequest)
              }
            }
        }
      }
    }

  lazy val token: Route =
    path("token") {
      post {
        formField("grant_type") {
          case "authorization_code" =>
            formFields("code", "redirect_uri".?, "client_id") { (code, redirectUri, clientId) =>
              run(code, GenerateAccessToken(clientId, code, redirectUri)) completeWith {
                case r: AccessTokenGenerated =>
                  complete(
                    StatusCodes.OK,
                    Tokens(
                      r.accessToken.token,
                      r.accessToken.tokenType.toLowerCase(),
                      AccountSettings.AccessTokenExpirationTime * 60,
                      r.accessToken.refreshToken
                    )
                  )
                case error: AccountErrorMessage =>
                  complete(
                    StatusCodes.BadRequest,
                    Map(
                      "error"             -> "access_denied",
                      "error_description" -> error.message
                    )
                  )
                case _ => complete(StatusCodes.BadRequest)
              }
            }
          case "refresh_token" =>
            formField("refresh_token") { refreshToken =>
              run(refreshToken, RefreshAccessToken(refreshToken)) completeWith {
                case r: AccessTokenRefreshed =>
                  complete(
                    StatusCodes.OK,
                    Tokens(
                      r.accessToken.token,
                      r.accessToken.tokenType.toLowerCase(),
                      AccountSettings.AccessTokenExpirationTime * 60,
                      r.accessToken.refreshToken
                    )
                  )
                case error: AccountErrorMessage =>
                  complete(
                    StatusCodes.BadRequest,
                    Map(
                      "error"             -> "access_denied",
                      "error_description" -> error.message
                    )
                  )
                case _ => complete(StatusCodes.BadRequest)
              }
            }
          case _ => complete(StatusCodes.BadRequest) // TODO client_credentials
        }
      }
    }

  lazy val me: Route = path("me") {
    get {
      handleRejections(
        RejectionHandler
          .newBuilder()
          .handleAll[AuthenticationFailedRejection](authenticationFailedRejectionHandler)
          .result()
      ) {
        authenticateOAuth2Async(AccountSettings.Realm, oauth) { account =>
          // create a new session
          val session = Session(account.uuid)
          session += (Session.adminKey, account.isAdmin)
          session += (Session.anonymousKey, false)
          account.currentProfile match {
            case Some(profile) =>
              session += (profileKey, profile.name)
            case _ =>
          }
          service.setSession(session) {
            // create a new anti csrf token
            setNewCsrfToken(checkHeader) {
              complete(
                HttpResponse(
                  StatusCodes.OK,
                  entity = account.details
                    .map(d => Me(d.firstName, d.lastName, d.email))
                    .getOrElse(Me("", ""))
                )
              )
            }
          }
        }
      }
    }
  }

  private def generateCode(
    uuid: String,
    clientId: String,
    scope: Option[String],
    redirectUri: Option[String],
    state: Option[String]
  ): Route = {
    run(uuid, GenerateAuthorizationCode(clientId, scope, redirectUri, state)) completeWith {
      case r: AuthorizationCodeGenerated =>
        redirectUri match {
          case Some(uri) =>
            redirect(
              redirection(
                uri,
                Map("code" -> r.authorizationCode.code, "state" -> state.getOrElse(""))
              ),
              StatusCodes.Found
            )
          case _ => complete(r.authorizationCode)
        }
      case error: AccountErrorMessage =>
        redirectUri match {
          case Some(uri) =>
            redirect(
              redirection(
                uri,
                Map(
                  "error"             -> "access_denied",
                  "error_description" -> error.message,
                  "state"             -> state.getOrElse("")
                )
              ),
              StatusCodes.Found
            )
          case _ => complete(StatusCodes.BadRequest)
        }
      case _ => complete(StatusCodes.BadRequest) // TODO AuthorizationCodeAlreadyExists
    }
  }

}

trait BasicOAuthService extends OAuthService with BasicAccountTypeKey
