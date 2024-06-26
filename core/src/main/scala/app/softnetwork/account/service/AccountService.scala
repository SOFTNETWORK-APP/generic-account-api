package app.softnetwork.account.service

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{AuthenticationFailedRejection, RejectionHandler, Route}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import app.softnetwork.api.server._
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.message._
import app.softnetwork.account.model._
import app.softnetwork.account.serialization._
import app.softnetwork.session.service.SessionMaterials
import org.json4s.jackson.Serialization
import org.json4s.{jackson, Formats}

import scala.language.implicitConversions
import akka.actor.typed.ActorSystem
import app.softnetwork.persistence._
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import com.softwaremill.session.SessionConfig

/** Created by smanciot on 23/04/2020.
  */
trait AccountService[PV <: ProfileView, DV <: AccountDetailsView, AV <: AccountView[
  PV,
  DV
], SD <: SessionData with SessionDataDecorator[SD]]
    extends AccountServiceDirectives[SD]
    with DefaultComplete
    with Json4sSupport
    with ApiRoute {
  _: CommandTypeKey[AccountCommand] with ManifestWrapper[AV] with SessionMaterials[SD] =>

  type SU

  def asSignUp: Unmarshaller[HttpRequest, SU]

  implicit def toSignUp: SU => SignUp

  implicit def formats: Formats = accountFormats

  implicit def serialization: Serialization.type = jackson.Serialization

  implicit def sessionConfig: SessionConfig

  override implicit def ts: ActorSystem[_] = system

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
    implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
    post {
      pathEnd {
        optionalSession(sc, gt) { maybeSession =>
          val uuid = maybeSession match {
            case Some(session) if session.anonymous && session.id.nonEmpty => session.id
            case _                                                         => generateUUID()
          }
          // execute anonymous signUp
          run(uuid, SignUpAnonymous) completeWith {
            case r: AccountCreated =>
              val account = r.account
              // create a new session
              val session = companion.newSession.withId(account.uuid).withAnonymous(true)
              setSession(sc, st, session) {
                // create a new anti csrf token
                setNewCsrfToken(checkHeader) {
                  complete(
                    HttpResponse(
                      status = StatusCodes.Created,
                      entity = account.view.asInstanceOf[AV]
                    )
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
    implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
    post {
      entity(asSignUp) { signUp =>
        optionalSession(sc, gt) { maybeSession =>
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
                complete(
                  HttpResponse(status = StatusCodes.Created, entity = account.view.asInstanceOf[AV])
                )
              if (!AccountSettings.ActivationEnabled) {
                // create a new session
                val session =
                  companion.newSession.withId(account.uuid).withAnonymous(false)
                setSession(sc, st, session) {
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
    implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
    get {
      entity(as[Activate]) { activate =>
        // execute activate
        run(activate.token, activate) completeWith {
          case r: AccountActivated =>
            val account = r.account
            // create a new session
            setSession(sc, st, companion.newSession.withId(account.uuid)) {
              // create a new anti csrf token
              setNewCsrfToken(checkHeader) {
                complete(HttpResponse(StatusCodes.OK, entity = account.view.asInstanceOf[AV]))
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
    implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
    post {
      handleRejections(
        RejectionHandler
          .newBuilder()
          .handleAll[AuthenticationFailedRejection](authenticationFailedRejectionHandler)
          .result()
      ) {
        authenticateBasicAsync(AccountSettings.Realm, basicAuth) { account =>
          // create a new session
          var session =
            companion.newSession
              .withId(account.uuid)
              .withAnonymous(false)
              .withAdmin(account.isAdmin)
          account.currentProfile match {
            case Some(profile) =>
              session += (session.profileKey, profile.name)
            case _ =>
          }
          setSession(sc, st, session) {
            // create a new anti csrf token
            setNewCsrfToken(checkHeader) {
              complete(HttpResponse(StatusCodes.OK, entity = account.view.asInstanceOf[AV]))
            }
          }
        }
      }
    }
  }

  lazy val login: Route = path("login" | "signIn") {
    implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
    post {
      entity(as[Login]) { login =>
        optionalSession(sc, gt) { maybeSession =>
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
              var session =
                companion.newSession
                  .withId(account.uuid)
                  .withAnonymous(false)
                  .withAdmin(account.isAdmin)
              account.currentProfile match {
                case Some(profile) =>
                  session += (session.profileKey, profile.name)
                case _ =>
              }
              setSession(sc, st, session) {
                // create a new anti csrf token
                setNewCsrfToken(checkHeader) {
                  complete(HttpResponse(StatusCodes.OK, entity = account.view.asInstanceOf[AV]))
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
        requiredSession(sc, gt) { session =>
          run(session.id, Logout) completeWith {
            case _: LogoutSucceeded.type =>
              // invalidate session
              invalidateSession(sc, gt) {
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
    implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
    // check anti CSRF token
    hmacTokenCsrfProtection(checkHeader) {
      post {
        // check if a session exists
        requiredSession(sc, gt) { session =>
          run(session.id, Unsubscribe(session.id)) completeWith {
            case r: AccountDeleted =>
              // invalidate session
              invalidateSession(sc, gt) {
                complete(
                  HttpResponse(status = StatusCodes.OK, entity = r.account.view.asInstanceOf[AV])
                )
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
              val session = companion.newSession.withId(r.uuid).withAnonymous(false)
              setSession(sc, st, session) {
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
      requiredSession(sc, gt) { session =>
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
      requiredSession(sc, gt) { session =>
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
      requiredSession(sc, gt) { session =>
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

}
