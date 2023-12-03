package app.softnetwork.account.service

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{AuthenticationFailedRejection, RejectionHandler, Route}
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.message._
import app.softnetwork.account.serialization._
import app.softnetwork.api.server._
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{jackson, Formats}
import org.json4s.jackson.Serialization
import akka.actor.typed.ActorSystem
import app.softnetwork.account.handlers.BasicAccountTypeKey
import app.softnetwork.account.spi.OAuth2Service
import app.softnetwork.persistence.generateUUID
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import com.softwaremill.session.SessionConfig

import scala.util.{Failure, Success}

trait OAuthService[SD <: SessionData with SessionDataDecorator[SD]]
    extends AccountServiceDirectives[SD]
    with DefaultComplete
    with Json4sSupport
    with StrictLogging
    with ApiRoute { _: CommandTypeKey[AccountCommand] with SessionMaterials[SD] =>

  implicit def sessionConfig: SessionConfig

  override implicit def ts: ActorSystem[_] = system

  implicit def formats: Formats = accountFormats

  implicit def serialization: Serialization.type = jackson.Serialization

  override val route: Route = {
    pathPrefix(AccountSettings.OAuthPath) {
      concat(authorize ~ token ~ me :: (signin ++ backup).toList: _*)
    }
  }

  lazy val authorize: Route =
    path("authorize") {
      get {
        parameter("response_type") {
          case "code" =>
            parameters("client_id", "redirect_uri".?, "scope".?, "state".?) {
              (clientId, redirectUri, scope, state) =>
                optionalSession(sc, gt) {
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
                      AccountSettings.OAuthSettings.accessToken.expirationTime * 60,
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
                      AccountSettings.OAuthSettings.accessToken.expirationTime * 60,
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
        authenticateOAuth2Async(AccountSettings.Realm, oauth) { case (account, application) =>
          // create a new session
          var session = companion.newSession
            .withId(account.uuid)
            .withAdmin(account.isAdmin)
            .withAnonymous(false)
          session += ("client_id", application.clientId)
          session += ("scope", application.accessToken.flatMap(_.scope).getOrElse(""))
          account.currentProfile match {
            case Some(profile) =>
              session += (session.profileKey, profile.name)
            case _ =>
          }
          setSession(sc, st, session) {
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

  lazy val services: Seq[OAuth2Service] = OAuth2Service()

  lazy val signin: Seq[Route] =
    for (service <- services) yield path(service.networkName / "signin") {
      get {
        redirect(
          service.authorizationUrl,
          StatusCodes.Found
        )
      }
    }

  lazy val backup: Seq[Route] =
    for (service <- services) yield path(service.networkName / "backup") {
      get {
        parameter("code") { code =>
          service.userInfo(code) match {
            case Success(s) =>
              val data = service.extractData(s)
              data.login match {
                case Some(login) =>
                  lookup(login) completeWith {
                    case Some(uuid) =>
                      // create a new session
                      var session = companion.newSession.withId(uuid).withAnonymous(false)
                      session += ("provider", data.provider)
                      setSession(sc, st, session) {
                        // create a new anti csrf token
                        setNewCsrfToken(checkHeader) {
                          complete(
                            HttpResponse(
                              StatusCodes.OK
                            )
                          )
                        }
                      }
                    case _ =>
                      run(generateUUID(), SignUpOAuth(data)) completeWith {
                        case r: AccountCreated =>
                          // create a new session
                          var session =
                            companion.newSession.withId(r.account.uuid).withAnonymous(false)
                          session += ("provider", data.provider)
                          session ++= data.data.toSeq
                          setSession(sc, st, session) {
                            // create a new anti csrf token
                            setNewCsrfToken(checkHeader) {
                              complete(
                                HttpResponse(
                                  StatusCodes.OK
                                )
                              )
                            }
                          }
                        case error: AccountErrorMessage =>
                          complete(
                            HttpResponse(StatusCodes.BadRequest, entity = error)
                          )
                        case _ => complete(StatusCodes.BadRequest)
                      }
                  }
                case _ =>
                  log.error(s"login not found within $data for ${service.networkName}")
                  complete(HttpResponse(StatusCodes.InternalServerError))
              }
            case Failure(f) =>
              logger.error(f.getMessage, f)
              complete(HttpResponse(StatusCodes.InternalServerError))
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

trait BasicOAuthService[SD <: SessionData with SessionDataDecorator[SD]]
    extends OAuthService[SD]
    with BasicAccountTypeKey { _: SessionMaterials[SD] => }
