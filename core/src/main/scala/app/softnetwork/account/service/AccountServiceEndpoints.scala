package app.softnetwork.account.service

import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.message._
import app.softnetwork.account.model.{AccountView, DeviceRegistration}
import app.softnetwork.account.serialization.accountFormats
import app.softnetwork.api.server.{ApiEndpoint, ApiErrors}
import app.softnetwork.concurrent.Completion
import app.softnetwork.persistence.{generateUUID, ManifestWrapper}
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.session.service.SessionEndpoints
import com.softwaremill.session.{
  GetSessionTransport,
  SetSessionTransport,
  TapirCsrfCheckMode,
  TapirEndpoints,
  TapirSessionContinuity
}
import org.json4s.Formats
import org.softnetwork.session.model.Session
import org.softnetwork.session.model.Session.profileKey
import sttp.capabilities
import sttp.capabilities.akka.AkkaStreams
import sttp.model.headers.{CookieValueWithMeta, WWWAuthenticateChallenge}
import sttp.model.{Method, StatusCode}
import sttp.monad.FutureMonad
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.json4s.jsonBody
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.{PartialServerEndpointWithSecurityOutput, ServerEndpoint}

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure, Success}

trait AccountServiceEndpoints[SU]
    extends BaseAccountService
    with ApiEndpoint
    with TapirEndpoints
    with Completion
    with ManifestWrapper[SU] {
  _: CommandTypeKey[AccountCommand] =>

  import app.softnetwork.serialization.serialization

  override implicit def formats: Formats = accountFormats

  def sessionEndpoints: SessionEndpoints

  def sc: TapirSessionContinuity[Session] = sessionEndpoints.sc

  def st: SetSessionTransport = sessionEndpoints.st

  def gt: GetSessionTransport = sessionEndpoints.gt

  def checkMode: TapirCsrfCheckMode[Session] = sessionEndpoints.checkMode

  def error(e: AccountCommandResult): ApiErrors.ErrorInfo = e match {
    case LoginAndPasswordNotMatched => ApiErrors.Unauthorized(LoginAndPasswordNotMatched)
    case AccountDisabled            => ApiErrors.Unauthorized(AccountDisabled)
    case AccountNotFound            => ApiErrors.NotFound(AccountNotFound)
    case ProfileNotFound            => ApiErrors.NotFound(ProfileNotFound)
    case TokenNotFound              => ApiErrors.NotFound(TokenNotFound)
    case CodeNotFound               => ApiErrors.NotFound(CodeNotFound)
    case r: AccountErrorMessage     => ApiErrors.BadRequest(r.message)
    case _                          => ApiErrors.BadRequest("Unknown")
  }

  val anonymous: ServerEndpoint[Any with AkkaStreams, Future] =
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        val partial = optionalSession(sc, st)

        partial.endpoint
          .errorOut(errors)
          .out(
            partial.securityOutput.and(jsonBody[AccountView]).and(statusCode(StatusCode.Created))
          )
          .serverSecurityLogicWithOutput(inputs =>
            partial.securityLogic(new FutureMonad())(inputs).map {

              case Left(_) => Left(ApiErrors.BadRequest(""))

              case Right(r) =>
                val maybeSession = r._2

                val uuid =
                  maybeSession match {
                    case Some(session) if session.anonymous && session.id.nonEmpty => session.id
                    case _                                                         => generateUUID()
                  }

                run(uuid, SignUpAnonymous) complete () match {

                  case Success(value) =>
                    value match {

                      case result: AccountCreated =>
                        val account = result.account
                        // create a new session
                        val session = Session(account.uuid)
                        session += (Session.anonymousKey, true)
                        Right(((r._1, account.view), Some(session)))

                      case other => Left(error(other))
                    }

                  case Failure(_) => Left(ApiErrors.InternalServerError("InternalServerError"))
                }
            }
          )
      }
    }.in(AccountSettings.Path / "anonymous")
      .post
      .serverLogicSuccess(_ => _ => Future.successful(Right()))

  implicit def SUS: Schema[SU]

  implicit def toSignUp: SU => SignUp

  lazy val signUp: ServerEndpoint[Any with AkkaStreams, Future] = {
    implicit val manifest: Manifest[SU] = manifestWrapper.wrapped
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        val partial = optionalSession(sc, st)

        partial.endpoint
          .prependSecurityIn(jsonBody[SU])
          .errorOut(errors)
          .out(
            partial.securityOutput.and(jsonBody[AccountView]).and(statusCode(StatusCode.Created))
          )
          .serverSecurityLogicWithOutput(inputs =>
            partial.securityLogic(new FutureMonad())(inputs._2).map {

              case Left(_) => Left(ApiErrors.BadRequest(""))

              case Right(r) =>
                val signUp: SignUp = inputs._1
                val maybeSession = r._2
                val uuid =
                  maybeSession match {
                    case Some(session) if session.anonymous && session.id.nonEmpty => session.id
                    case _                                                         => generateUUID()
                  }
                // execute signUp
                run(
                  uuid,
                  signUp
                ) complete () match {
                  case Success(value) =>
                    value match {

                      case result: AccountCreated =>
                        val account = result.account
                        if (!AccountSettings.ActivationEnabled) {
                          // create a new session
                          val session = Session(account.uuid)
                          session += (Session.anonymousKey, false)
                          Right((r._1, account.view), Some(session))
                        } else {
                          Right(((r._1, account.view), maybeSession))
                        }

                      case other => Left(error(other))
                    }

                  case Failure(_) => Left(ApiErrors.InternalServerError("InternalServerError"))
                }
            }
          )
      }
    }.in(AccountSettings.Path / "signUp")
      .post
      .serverLogicSuccess(_ => _ => Future.successful(Right()))
  }

  val principal: ServerEndpoint[Any with AkkaStreams, Future] =
    hmacTokenCsrfProtection(checkMode) {
      val partial =
        requiredSession(sc, st)
      partial.endpoint
        .securityIn(jsonBody[UpdateLogin])
        .out(partial.securityOutput)
        .errorOut(errors)
        .serverSecurityLogicWithOutput { inputs =>
          partial.securityLogic(new FutureMonad())(inputs._1).flatMap {
            case Left(_) => Future.successful(Left(ApiErrors.Unauthorized("Unauthorized")))
            case Right(r) =>
              val session = r._2
              val login = inputs._2
              run(session.id, login).map {
                case LoginUpdated => Right(r._1, session)
                case other        => Left(error(other))
              }
          }
        }
    }
      .in(AccountSettings.Path / "principal")
      .post
      .serverLogicSuccess(_ => _ => Future.successful(()))

  val basic: ServerEndpoint[Any with AkkaStreams, Future] =
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        endpoint
          .securityIn(
            auth.basic[UsernamePassword](WWWAuthenticateChallenge.basic(AccountSettings.Realm))
          )
          .errorOut(errors)
          .out(jsonBody[AccountView])
          .serverSecurityLogicWithOutput { up =>
            run(up.username, Login(up.username, up.password.getOrElse(""))).map {
              case r: LoginSucceededResult =>
                val account = r.account
                // create a new session
                val session = Session(account.uuid)
                session += (Session.adminKey, account.isAdmin)
                session += (Session.anonymousKey, false)
                Right((account.view, Some(session)))
              case other => Left(error(other))
            }
          }
      }
    }.in(AccountSettings.Path / "basic")
      .post
      .serverLogicSuccess(_ => _ => Future.successful(()))

  val loginEndpoint: PartialServerEndpointWithSecurityOutput[
    ((Login, Seq[Option[String]]), Seq[Option[String]]),
    Option[Session],
    Unit,
    ApiErrors.ErrorInfo,
    (((Seq[Option[String]], AccountView), Seq[Option[String]]), Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] =
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        val partial = optionalSession(sc, st)

        partial.endpoint
          .prependSecurityIn(jsonBody[Login])
          .errorOut(errors)
          .out(partial.securityOutput.and(jsonBody[AccountView]))
          .serverSecurityLogicWithOutput(inputs =>
            partial.securityLogic(new FutureMonad())(inputs._2).map {

              case Left(_) => Left(ApiErrors.BadRequest(""))

              case Right(r) =>
                val login: Login = inputs._1
                val maybeSession = r._2
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
                ) complete () match {

                  case Success(value) =>
                    value match {

                      case result: LoginSucceededResult =>
                        val account = result.account
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
                        Right(((r._1, account.view), Some(session)))

                      case other => Left(error(other))
                    }

                  case Failure(_) => Left(ApiErrors.InternalServerError("InternalServerError"))
                }
            }
          )
      }
    }.in(AccountSettings.Path).post

  val login: ServerEndpoint[Any with AkkaStreams, Future] =
    loginEndpoint.in("login").serverLogicSuccess(_ => _ => Future.successful(()))

  val signIn: ServerEndpoint[Any with AkkaStreams, Future] =
    loginEndpoint.in("signIn").serverLogicSuccess(_ => _ => Future.successful(()))

  val activate: ServerEndpoint[Any with AkkaStreams, Future] =
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        endpoint
          .securityIn(jsonBody[Activate])
          .errorOut(errors)
          .serverSecurityLogicWithOutput(activate =>
            run(activate.token, activate).map {
              case r: AccountActivated =>
                val account = r.account
                // create a new session
                Right((), Some(Session(account.uuid)))
              case other => Left(error(other))
            }
          )
      }
    }
      .in(AccountSettings.Path / "activate")
      .get
      .serverLogicSuccess(_ => _ => Future.successful(()))

  val logoutEndpoint: PartialServerEndpointWithSecurityOutput[
    ((Seq[Option[String]], Seq[Option[String]]), Option[String], Method, Option[String]),
    Session,
    Unit,
    _,
    (Seq[Option[String]], Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] = {
    val partial =
      hmacTokenCsrfProtection(checkMode) {
        invalidateSession(sc, gt) {
          requiredSession(sc, st)
        }
      }
    partial.endpoint
      .out(partial.securityOutput)
      .errorOut(errors)
      .serverSecurityLogicWithOutput { inputs =>
        partial.securityLogic(new FutureMonad())(inputs).flatMap {
          case Left(_) => Future.successful(Left(ApiErrors.Unauthorized("Unauthorized")))
          case Right(r) =>
            val session = r._2
            run(session.id, Logout).map {
              case LogoutSucceeded => Right(r._1, session)
              case other           => Left(error(other))
            }
        }
      }
  }

  val logout: ServerEndpoint[Any with AkkaStreams, Future] =
    logoutEndpoint
      .in(AccountSettings.Path / "logout")
      .serverLogicSuccess(_ => _ => Future.successful(()))

  val signOut: ServerEndpoint[Any with AkkaStreams, Future] =
    logoutEndpoint
      .in(AccountSettings.Path / "signOut")
      .serverLogicSuccess(_ => _ => Future.successful(()))

  val sendVerificationCode: ServerEndpoint[Any with AkkaStreams, Future] =
    setNewCsrfToken(checkMode) {
      endpoint
        .securityIn(jsonBody[SendVerificationCode])
        .errorOut(errors)
        .serverSecurityLogicWithOutput { verificationCode =>
          run(
            verificationCode.principal,
            verificationCode
          ).map {
            case VerificationCodeSent => Right((), ())
            case other                => Left(error(other))
          }
        }
    }
      .in(AccountSettings.Path / "verificationCode")
      .post
      .serverLogicSuccess(_ => _ => Future.successful(()))

  val sendResetPasswordToken: ServerEndpoint[Any with AkkaStreams, Future] =
    endpoint
      .in(AccountSettings.Path / "resetPasswordToken")
      .post
      .in(jsonBody[SendResetPasswordToken])
      .errorOut(errors)
      .serverLogic { resetPasswordToken =>
        run(
          resetPasswordToken.principal,
          resetPasswordToken
        ).map {
          case ResetPasswordTokenSent => Right(())
          case other                  => Left(error(other))
        }
      }

  val checkResetPasswordToken: ServerEndpoint[Any with AkkaStreams, Future] = {
    val partial =
      setNewCsrfToken(checkMode) {
        endpoint.serverSecurityLogicSuccessWithOutput(_ => Future.successful(((), ())))
      }
    partial.endpoint
      .in(AccountSettings.Path / "resetPasswordToken")
      .in(path[String])
      .out(partial.securityOutput)
      .errorOut(errors)
      .get
      .serverLogic(token =>
        partial.securityLogic(new FutureMonad())().map {
          case Left(_) => Left(ApiErrors.Unauthorized("Unauthorized"))
          case Right(r) =>
            run(
              token,
              CheckResetPasswordToken(token)
            ) complete () match {
              case Success(value) =>
                value match {
                  case ResetPasswordTokenChecked => Right(r._1)
                  case other                     => Left(error(other))
                }
              case Failure(_) => Left(ApiErrors.InternalServerError("InternalServerError"))
            }
        }
      )
  }

  val resetPassword: ServerEndpoint[Any with AkkaStreams, Future] =
    hmacTokenCsrfProtection(checkMode) {
      setSession(sc, st) {
        endpoint
          .securityIn(jsonBody[ResetPassword])
          .errorOut(errors)
          .serverSecurityLogicWithOutput { reset =>
            run(reset.token, reset).map {
              case r: PasswordReseted =>
                // create a new session
                val session = Session(r.uuid)
                session += (Session.anonymousKey, false)
                Right((), Some(session))
              case other => Left(error(other))
            }
          }
      }
    }
      .in(AccountSettings.Path / "resetPassword")
      .post
      .serverLogicSuccess(_ => _ => Future.successful(()))

  val unsubscribe: ServerEndpoint[Any with AkkaStreams, Future] =
    hmacTokenCsrfProtection(checkMode) {
      invalidateSession(sc, st) {
        requiredSession(sc, st)
      }
    }
      .in(AccountSettings.Path / "unsubscribe")
      .post
      .out(jsonBody[AccountView])
      .serverLogic(session =>
        _ =>
          run(session.id, Unsubscribe(session.id)).map {
            case result: AccountDeleted => Right(result.account.view)
            case _                      => Left(())
          }
      )

  val registerDevice: ServerEndpoint[Any with AkkaStreams, Future] =
    hmacTokenCsrfProtection(checkMode) {
      requiredSession(sc, st)
    }
      .in(AccountSettings.Path / "device")
      .in(jsonBody[DeviceRegistration])
      .post
      .serverLogic(session =>
        registration =>
          run(
            session.id,
            RegisterDevice(
              session.id,
              registration
            )
          ).map {
            case DeviceRegistered => Right(())
            case other            => Left(())
          }
      )

  val unregisterDevice: ServerEndpoint[Any with AkkaStreams, Future] = {
    hmacTokenCsrfProtection(checkMode) {
      requiredSession(sc, st)
    }
      .in(AccountSettings.Path / "device")
      .in(path[String])
      .delete
      .serverLogic(session =>
        regId =>
          run(session.id, UnregisterDevice(session.id, regId)).map {
            case DeviceUnregistered => Right(())
            case other              => Left(())
          }
      )
  }

  val updatePassword: ServerEndpoint[Any with AkkaStreams, Future] =
    hmacTokenCsrfProtection(checkMode) {
      requiredSession(sc, st)
    }
      .in(AccountSettings.Path / "password")
      .in(jsonBody[PasswordData])
      .post
      .serverLogic(session =>
        data => {
          import data._
          run(
            session.id,
            UpdatePassword(session.id, oldPassword, newPassword, confirmedPassword)
          ).map {
            case _: PasswordUpdated => Right(((), session))
            case other              => Left(error(other))
          }
        }
      )

  override lazy val endpoints
    : List[ServerEndpoint[AkkaStreams with capabilities.WebSockets, Future]] =
    List(
      anonymous,
      signUp,
      principal,
      basic,
      login,
      signIn,
      activate,
      logout,
      signOut,
      sendVerificationCode,
      sendResetPasswordToken,
      checkResetPasswordToken,
      resetPassword,
      unsubscribe,
      registerDevice,
      unregisterDevice,
      updatePassword
    )
}
