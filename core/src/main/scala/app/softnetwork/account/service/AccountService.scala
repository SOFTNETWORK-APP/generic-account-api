package app.softnetwork.account.service

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.unmarshalling.Unmarshaller
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import app.softnetwork.api.server._
import app.softnetwork.concurrent.Completion.AwaitCompletion
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.message._
import app.softnetwork.account.model._
import app.softnetwork.account.serialization._
import org.softnetwork.session.model.Session
import app.softnetwork.session.service.SessionService
import org.json4s.jackson.Serialization
import org.json4s.{jackson, Formats}

import scala.language.implicitConversions
import scala.util.{Failure, Success}
import Session._
import app.softnetwork.persistence._

/** Created by smanciot on 23/04/2020.
  */
trait AccountService
    extends BaseAccountService
    with Directives
    with DefaultComplete
    with Json4sSupport
    with StrictLogging { _: CommandTypeKey[AccountCommand] =>

  type SU

  def asSignUp: Unmarshaller[HttpRequest, SU]

  type PV = DefaultProfileView

  type DV = DefaultAccountDetailsView

  type AV = DefaultAccountView[PV, DV]

//  implicit def accountViewToResponseEntity: AV => ResponseEntity

  implicit def toSignUp: SU => SignUp

  implicit def formats: Formats = accountFormats

  implicit def serialization: Serialization.type = jackson.Serialization

  def sessionService: SessionService

  val route: Route = {
    pathPrefix(AccountSettings.Path) {
      anonymous ~
      signUp ~
      principal ~
      basic ~
      login ~
      activate ~
      logout ~
      verificationCode ~
      resetPasswordToken ~
      resetPassword ~
      unsubscribe ~
      device ~
      password
    }
  }

  lazy val anonymous: Route = path("anonymous") {
    post {
      pathEnd {
        sessionService.optionalSession { maybeSession =>
          val uuid = maybeSession match {
            case Some(session) if session.anonymous && session.id.nonEmpty => session.id
            case _                                                         => generateUUID()
          }
          // execute anonymous signUp
          run(uuid, SignUpAnonymous) completeWith {
            case r: AccountCreated =>
              val account = r.account
              // create a new session
              val session = Session(account.uuid)
              session += (Session.anonymousKey, true)
              sessionService.setSession(session) {
                // create a new anti csrf token
                setNewCsrfToken(checkHeader) {
                  complete(
                    HttpResponse(status = StatusCodes.Created, entity = account.view[PV, DV])
                  )
                }
              }
            case error: AccountErrorMessage =>
              complete(HttpResponse(StatusCodes.BadRequest, entity = error))
            case _ => complete(HttpResponse(StatusCodes.BadRequest))
          }
        }
      }
    }
  }

  lazy val signUp: Route = path("signUp") {
    post {
      entity(asSignUp) { signUp =>
        sessionService.optionalSession { maybeSession =>
          val uuid = maybeSession match {
            case Some(session) if session.anonymous && session.id.nonEmpty => session.id
            case _                                                         => generateUUID()
          }
          // execute signUp
          run(
            uuid,
            signUp
          ) completeWith {
            case r: AccountCreated =>
              val account = r.account
              lazy val completion =
                complete(HttpResponse(status = StatusCodes.Created, entity = account.view[PV, DV]))
              if (!AccountSettings.ActivationEnabled) {
                // create a new session
                val session = Session(account.uuid)
                session += (Session.anonymousKey, false)
                sessionService.setSession(session) {
                  // create a new anti csrf token
                  setNewCsrfToken(checkHeader) {
                    completion
                  }
                }
              } else {
                completion
              }
            case error: AccountErrorMessage =>
              complete(HttpResponse(StatusCodes.BadRequest, entity = error))
            case _ => complete(HttpResponse(StatusCodes.BadRequest))
          }
        }
      }
    }
  }

  lazy val activate: Route = path("activate") {
    get {
      entity(as[Activate]) { activate =>
        // execute activate
        run(activate.token, activate) completeWith {
          case r: AccountActivated =>
            val account = r.account
            // create a new session
            sessionService.setSession(Session(account.uuid)) {
              // create a new anti csrf token
              setNewCsrfToken(checkHeader) {
                complete(HttpResponse(StatusCodes.OK, entity = account.view[PV, DV]))
              }
            }
          case error: AccountErrorMessage =>
            complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _ => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val basic: Route = path("basic") {
    post {
      authenticateBasic(AccountSettings.Realm, BasicAuthAuthenticator) { account =>
        // create a new session
        val session = Session(account.uuid)
        session += (Session.adminKey, account.isAdmin)
        session += (Session.anonymousKey, false)
        sessionService.setSession(session) {
          // create a new anti csrf token
          setNewCsrfToken(checkHeader) {
            complete(HttpResponse(StatusCodes.OK, entity = account.view[PV, DV]))
          }
        }
      }
    }
  }

  lazy val login: Route = path("login" | "signIn") {
    post {
      entity(as[Login]) { login =>
        sessionService.optionalSession { maybeSession =>
          // execute Login
          run(
            login.login,
            login
              .copy(anonymous =
                maybeSession.flatMap(session =>
                  if (session.anonymous && session.id.nonEmpty)
                    Some(session.id)
                  else
                    None
                )
              )
          ) completeWith {
            case r: LoginSucceededResult =>
              val account = r.account
              // create a new session
              val session = Session(
                account.uuid
                /** FIXME login.refreshable * */
              )
              session += (Session.adminKey, account.isAdmin)
              session += (Session.anonymousKey, false)
              account.currentProfile match {
                case Some(profile) =>
                  session += (profileKey, profile.name)
                case _ =>
              }
              sessionService.setSession(session) {
                // create a new anti csrf token
                setNewCsrfToken(checkHeader) {
                  complete(HttpResponse(StatusCodes.OK, entity = account.view[PV, DV]))
                }
              }
            case error: AccountErrorMessage =>
              complete(HttpResponse(StatusCodes.Unauthorized, entity = error))
            case _ => complete(HttpResponse(StatusCodes.BadRequest))
          }
        }
      }
    }
  }

  lazy val logout: Route = path("logout" | "signOut") {
    // check anti CSRF token
    hmacTokenCsrfProtection(checkHeader) {
      post {
        // check if a session exists
        sessionService.requiredSession { session =>
          run(session.id, Logout) completeWith {
            case _: LogoutSucceeded.type =>
              // invalidate session
              sessionService.invalidateSession {
                complete(HttpResponse(StatusCodes.OK, entity = Map[String, String]()))
              }
            case error: AccountErrorMessage =>
              complete(HttpResponse(StatusCodes.BadRequest, entity = error))
            case _ => complete(HttpResponse(StatusCodes.BadRequest))
          }
        }
      }
    }
  }

  lazy val unsubscribe: Route = path("unsubscribe") {
    // check anti CSRF token
    hmacTokenCsrfProtection(checkHeader) {
      post {
        // check if a session exists
        sessionService.requiredSession { session =>
          run(session.id, Unsubscribe(session.id)) completeWith {
            case r: AccountDeleted =>
              // invalidate session
              sessionService.invalidateSession {
                complete(HttpResponse(status = StatusCodes.OK, entity = r.account.view[PV, DV]))
              }
            case error: AccountErrorMessage =>
              complete(HttpResponse(StatusCodes.BadRequest, entity = error))
            case _ => complete(HttpResponse(StatusCodes.BadRequest))
          }
        }
      }
    }
  }

  lazy val verificationCode: Route = path("verificationCode") {
    post {
      entity(as[SendVerificationCode]) { verificationCode =>
        run(
          verificationCode.principal
          /** #MOSA-454*
            */,
          verificationCode
        ) completeWith {
          case _: VerificationCodeSent.type =>
            // create a new anti csrf token
            setNewCsrfToken(checkHeader) {
              complete(HttpResponse(status = StatusCodes.OK))
            }
          case error: AccountErrorMessage =>
            complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _ => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val resetPasswordToken: Route = pathPrefix("resetPasswordToken") {
    pathSuffix(Segment) { token =>
      get {
        run(token, CheckResetPasswordToken(token)) completeWith {
          case _: ResetPasswordTokenChecked.type =>
            // create a new anti csrf token
            setNewCsrfToken(checkHeader) {
              complete(HttpResponse(status = StatusCodes.OK))
            }
          case error: AccountErrorMessage =>
            complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _ => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    } ~ post {
      entity(as[SendResetPasswordToken]) { resetPasswordToken =>
        run(
          resetPasswordToken.principal
          /** #MOSA-454*
            */,
          resetPasswordToken
        ) completeWith {
          case _: ResetPasswordTokenSent.type => complete(HttpResponse(status = StatusCodes.OK))
          case error: AccountErrorMessage =>
            complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _ => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val resetPassword: Route = path("resetPassword") {
    // check anti CSRF token
    hmacTokenCsrfProtection(checkHeader) {
      post {
        entity(as[ResetPassword]) { reset =>
          run(reset.token, reset) completeWith {
            case r: PasswordReseted =>
              // create a new session
              val session = Session(r.uuid)
              session += (Session.anonymousKey, false)
              sessionService.setSession(session) {
                complete(HttpResponse(status = StatusCodes.OK))
              }
            case error: AccountErrorMessage =>
              complete(HttpResponse(StatusCodes.BadRequest, entity = error))
            case _ => complete(HttpResponse(StatusCodes.BadRequest))
          }
        }
      }
    }
  }

  lazy val device: Route = pathPrefix("device") {
    // check anti CSRF token
    hmacTokenCsrfProtection(checkHeader) {
      // check if a session exists
      sessionService.requiredSession { session =>
        post {
          entity(as[DeviceRegistration]) { registration =>
            run(
              session.id,
              RegisterDevice(
                session.id,
                registration
              )
            ) completeWith {
              case DeviceRegistered => complete(HttpResponse(status = StatusCodes.OK))
              case AccountNotFound  => complete(HttpResponse(status = StatusCodes.NotFound))
              case _                => complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        } ~
        pathPrefix(Segment) { regId =>
          delete {
            run(session.id, UnregisterDevice(session.id, regId)) completeWith {
              case DeviceUnregistered => complete(HttpResponse(status = StatusCodes.OK))
              case AccountNotFound    => complete(HttpResponse(status = StatusCodes.NotFound))
              case DeviceRegistrationNotFound =>
                complete(HttpResponse(status = StatusCodes.NotFound))
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        }
      }
    }
  }

  lazy val password: Route = pathPrefix("password") {
    // check anti CSRF token
    hmacTokenCsrfProtection(checkHeader) {
      // check if a session exists
      sessionService.requiredSession { session =>
        post {
          entity(as[PasswordData]) { data =>
            import data._
            run(
              session.id,
              UpdatePassword(session.id, oldPassword, newPassword, confirmedPassword)
            ) completeWith {
              case _: PasswordUpdated => complete(HttpResponse(status = StatusCodes.OK))
              case error: AccountErrorMessage =>
                complete(
                  HttpResponse(
                    StatusCodes.BadRequest,
                    entity = error
                  )
                )
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        }
      }
    }
  }

  lazy val principal: Route = path("principal") {
    // check anti CSRF token
    hmacTokenCsrfProtection(checkHeader) {
      // check if a session exists
      sessionService.requiredSession { session =>
        post {
          entity(as[UpdateLogin]) { login =>
            run(session.id, login) completeWith {
              case LoginUpdated => complete(HttpResponse(status = StatusCodes.OK))
              case error: AccountErrorMessage =>
                complete(
                  HttpResponse(
                    StatusCodes.BadRequest,
                    entity = error
                  )
                )
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        }
      }
    }
  }

  private def BasicAuthAuthenticator(credentials: Credentials): Option[Account] = {
    credentials match {
      case p @ Credentials.Provided(_) =>
        run(p.identifier, BasicAuth(p)) await {
          case r: LoginSucceededResult => Some(r.account)
          case _                       => None
        } match {
          case Failure(exception) =>
            logger.error(exception.getMessage, exception)
            None
          case Success(value) => value
        }
      case _ => None
    }
  }
}
