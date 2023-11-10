package app.softnetwork.account.service

import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.message._
import app.softnetwork.account.model.{
  AccountDetailsView,
  AccountStatus,
  AccountView,
  DeviceRegistration,
  ProfileView
}
import app.softnetwork.account.serialization.accountFormats
import app.softnetwork.api.server.ApiErrors
import app.softnetwork.persistence.{generateUUID, ManifestWrapper}
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.session.service.ServiceWithSessionEndpoints
import org.json4s.Formats
import org.softnetwork.session.model.Session
import org.softnetwork.session.model.Session.profileKey
import sttp.capabilities
import sttp.capabilities.akka.AkkaStreams
import sttp.model.headers.{CookieValueWithMeta, WWWAuthenticateChallenge}
import sttp.model.StatusCode
import sttp.monad.FutureMonad
import sttp.tapir._
import sttp.tapir.json.json4s.jsonBody
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.{PartialServerEndpointWithSecurityOutput, ServerEndpoint}

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure, Success}

trait AccountServiceEndpoints[SU]
    extends BaseAccountService
    with ServiceWithSessionEndpoints[AccountCommand, AccountCommandResult]
    with ManifestWrapper[SU] {
  _: CommandTypeKey[AccountCommand] =>

  import app.softnetwork.serialization.serialization

  override implicit def formats: Formats = accountFormats

  override implicit def resultToApiError(result: AccountCommandResult): ApiErrors.ErrorInfo =
    result match {
      case LoginAndPasswordNotMatched => ApiErrors.Unauthorized(LoginAndPasswordNotMatched)
      case AccountDisabled            => ApiErrors.Unauthorized(AccountDisabled)
      case AccountNotFound            => ApiErrors.NotFound(AccountNotFound)
      case ProfileNotFound            => ApiErrors.NotFound(ProfileNotFound)
      case TokenNotFound              => ApiErrors.NotFound(TokenNotFound)
      case CodeNotFound               => ApiErrors.NotFound(CodeNotFound)
      case r: AccountErrorMessage     => ApiErrors.BadRequest(r.message)
      case _                          => ApiErrors.BadRequest("Unknown")
    }

  type PV <: ProfileView

  type DV <: AccountDetailsView

  type AV <: AccountView[PV, DV]

  val manifest: Manifest[AV] = implicitly[Manifest[AV]]

  implicit def AVManifest: Manifest[AV] = manifest

  implicit def ASS: Schema[AccountStatus] = Schema.derived

  implicit def AVSchema: Schema[AV]

  val anonymous: ServerEndpoint[Any with AkkaStreams, Future] =
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        val partial = optionalSession(sc, gt)

        partial.endpoint
          .errorOut(ApiErrors.oneOfApiErrors)
          .out(
            partial.securityOutput.and(jsonBody[AV]).and(statusCode(StatusCode.Created))
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
                        Right(
                          (
                            (
                              r._1.map(_ => None) /*akka-http-session bug ?*/,
                              account.view.asInstanceOf[AV]
                            ),
                            Some(session)
                          )
                        )

                      case other => Left(resultToApiError(other))
                    }

                  case Failure(_) => Left(ApiErrors.InternalServerError("InternalServerError"))
                }
            }
          )
      }
    }
      .in(AccountSettings.Path / "anonymous")
      .post
      .serverLogicSuccess(_ => _ => Future.successful(()))

  implicit def SUS: Schema[SU]

  implicit def toSignUp: SU => SignUp

  lazy val signUp: ServerEndpoint[Any with AkkaStreams, Future] = {
    implicit val manifest: Manifest[SU] = manifestWrapper.wrapped
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        val partial = optionalSession(sc, gt)

        partial.endpoint
          .prependSecurityIn(jsonBody[SU])
          .errorOut(ApiErrors.oneOfApiErrors)
          .out(
            partial.securityOutput.and(jsonBody[AV]).and(statusCode(StatusCode.Created))
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
                          Right(
                            (
                              r._1.map(_ => None) /*akka-http-session bug ?*/,
                              account.view.asInstanceOf[AV]
                            ),
                            Some(session)
                          )
                        } else {
                          Right(((r._1, account.view.asInstanceOf[AV]), maybeSession))
                        }

                      case other => Left(resultToApiError(other))
                    }

                  case Failure(_) => Left(ApiErrors.InternalServerError("InternalServerError"))
                }
            }
          )
      }
    }
      .in(AccountSettings.Path / "signUp")
      .post
      .serverLogicSuccess(_ => _ => Future.successful(()))
  }

  val principal: ServerEndpoint[Any with AkkaStreams, Future] =
    hmacTokenCsrfProtection(checkMode) {
      val partial =
        requiredSession(sc, gt)
      partial.endpoint
        .securityIn(jsonBody[UpdateLogin])
        .out(partial.securityOutput)
        .errorOut(ApiErrors.oneOfApiErrors)
        .serverSecurityLogicWithOutput { inputs =>
          partial.securityLogic(new FutureMonad())(inputs._1).flatMap {
            case Left(_) => Future.successful(Left(ApiErrors.Unauthorized("Unauthorized")))
            case Right(r) =>
              val session = r._2
              val login = inputs._2
              run(session.id, login).map {
                case LoginUpdated => Right(r._1, session)
                case other        => Left(resultToApiError(other))
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
          .errorOut(ApiErrors.oneOfApiErrors)
          .out(jsonBody[AV])
          .serverSecurityLogicWithOutput { up =>
            run(up.username, Login(up.username, up.password.getOrElse(""))).map {
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
                Right((account.view.asInstanceOf[AV], Some(session)))
              case other => Left(resultToApiError(other))
            }
          }
      }
    }
      .in(AccountSettings.Path / "basic")
      .post
      .serverLogicSuccess(_ => _ => Future.successful(()))

  val loginEndpoint: PartialServerEndpointWithSecurityOutput[
    ((Login, Seq[Option[String]]), Seq[Option[String]]),
    Option[Session],
    Unit,
    ApiErrors.ErrorInfo,
    (((Seq[Option[String]], AV), Seq[Option[String]]), Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] =
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        val partial = optionalSession(sc, gt)
        partial.endpoint
          .prependSecurityIn(jsonBody[Login])
          .errorOut(ApiErrors.oneOfApiErrors)
          .out(partial.securityOutput.and(jsonBody[AV]))
          .serverSecurityLogicWithOutput(inputs =>
            partial.securityLogic(new FutureMonad())(inputs._2).map {

              case Left(_) => Left(ApiErrors.BadRequest("Unknown"))

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
                        Right(
                          (
                            (
                              r._1.map(_ => None) /*akka-http-session bug ?*/,
                              account.view.asInstanceOf[AV]
                            ),
                            Some(session)
                          )
                        )

                      case other => Left(resultToApiError(other))
                    }

                  case Failure(_) => Left(ApiErrors.InternalServerError("InternalServerError"))
                }
            }
          )
      }
    }
      .in(AccountSettings.Path)
      .post

  val login: ServerEndpoint[Any with AkkaStreams, Future] =
    loginEndpoint.in("login").serverLogicSuccess(_ => _ => Future.successful(()))

  val signIn: ServerEndpoint[Any with AkkaStreams, Future] =
    loginEndpoint.in("signIn").serverLogicSuccess(_ => _ => Future.successful(()))

  val activate: ServerEndpoint[Any with AkkaStreams, Future] =
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        endpoint
          .securityIn(jsonBody[Activate])
          .errorOut(ApiErrors.oneOfApiErrors)
          .serverSecurityLogicWithOutput(activate =>
            run(activate.token, activate).map {
              case r: AccountActivated =>
                val account = r.account
                // create a new session
                Right((), Some(Session(account.uuid)))
              case other => Left(resultToApiError(other))
            }
          )
      }
    }
      .in(AccountSettings.Path / "activate")
      .get
      .serverLogicSuccess(_ => _ => Future.successful(()))

  def logoutEndpoint(suffix: String): ServerEndpoint[Any with AkkaStreams, Future] =
    ApiErrors
      .withApiErrorVariants(
        invalidateSession(sc, gt) {
          antiCsrfWithRequiredSession(sc, gt, checkMode)
        }
      )
      .in(AccountSettings.Path / suffix)
      .serverLogic(session =>
        _ => {
          run(session.id, Logout).map {
            case LogoutSucceeded => Right(())
            case other           => Left(resultToApiError(other))
          }
        }
      )

  val logout: ServerEndpoint[Any with AkkaStreams, Future] = logoutEndpoint("logout")

  val signOut: ServerEndpoint[Any with AkkaStreams, Future] = logoutEndpoint("signOut")

  val sendVerificationCode: ServerEndpoint[Any with AkkaStreams, Future] =
    ApiErrors
      .withApiErrorVariants(
        setNewCsrfToken(checkMode) {
          endpoint.serverSecurityLogicSuccessWithOutput(_ => Future.successful(((), ())))
        }
      )
      .in(AccountSettings.Path / "verificationCode")
      .in(jsonBody[SendVerificationCode])
      .post
      .serverLogic(_ =>
        verificationCode => {
          run(
            verificationCode.principal,
            verificationCode
          ).map {
            case VerificationCodeSent => Right(())
            case other                => Left(resultToApiError(other))
          }
        }
      )

  val sendResetPasswordToken: ServerEndpoint[Any with AkkaStreams, Future] =
    endpoint
      .in(AccountSettings.Path / "resetPasswordToken")
      .post
      .in(jsonBody[SendResetPasswordToken])
      .errorOut(ApiErrors.oneOfApiErrors)
      .serverLogic { resetPasswordToken =>
        run(
          resetPasswordToken.principal,
          resetPasswordToken
        ).map {
          case ResetPasswordTokenSent => Right(())
          case other                  => Left(resultToApiError(other))
        }
      }

  val checkResetPasswordToken: ServerEndpoint[Any with AkkaStreams, Future] =
    ApiErrors
      .withApiErrorVariants(
        setNewCsrfToken(checkMode) {
          endpoint.serverSecurityLogicSuccessWithOutput(_ => Future.successful(((), ())))
        }
      )
      .in(AccountSettings.Path / "resetPasswordToken")
      .in(path[String])
      .get
      .serverLogic(_ =>
        token =>
          run(
            token,
            CheckResetPasswordToken(token)
          ).map {
            case ResetPasswordTokenChecked => Right(())
            case other                     => Left(resultToApiError(other))
          }
      )

  val resetPassword: ServerEndpoint[Any with AkkaStreams, Future] = {
    hmacTokenCsrfProtection(checkMode) {
      setSession(sc, st) {
        endpoint
          .securityIn(jsonBody[ResetPassword])
          .errorOut(ApiErrors.oneOfApiErrors)
          .serverSecurityLogicWithOutput { reset =>
            run(reset.token, reset).map {
              case r: PasswordReseted =>
                // create a new session
                val session = Session(r.uuid)
                session += (Session.anonymousKey, false)
                Right((), Some(session))
              case other => Left(resultToApiError(other))
            }
          }
      }
    }
      .in(AccountSettings.Path / "resetPassword")
      .post
      .serverLogicSuccess(_ => _ => Future.successful(()))
  }

  val unsubscribe: ServerEndpoint[Any with AkkaStreams, Future] =
    ApiErrors
      .withApiErrorVariants(
        invalidateSession(sc, gt) {
          antiCsrfWithRequiredSession(sc, gt, checkMode)
        }
      )
      .in(AccountSettings.Path / "unsubscribe")
      .post
      .out(jsonBody[AV])
      .serverLogic(session =>
        _ =>
          run(session.id, Unsubscribe(session.id)).map {
            case result: AccountDeleted => Right(result.account.view.asInstanceOf[AV])
            case other                  => Left(resultToApiError(other))
          }
      )

  val registerDevice: ServerEndpoint[Any with AkkaStreams, Future] =
    ApiErrors
      .withApiErrorVariants(
        antiCsrfWithRequiredSession(sc, gt, checkMode)
      )
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
            case other            => Left(resultToApiError(other))
          }
      )

  val unregisterDevice: ServerEndpoint[Any with AkkaStreams, Future] = {
    ApiErrors
      .withApiErrorVariants(
        antiCsrfWithRequiredSession(sc, gt, checkMode)
      )
      .in(AccountSettings.Path / "device")
      .in(path[String])
      .delete
      .serverLogic(session =>
        regId =>
          run(session.id, UnregisterDevice(session.id, regId)).map {
            case DeviceUnregistered => Right(())
            case other              => Left(resultToApiError(other))
          }
      )
  }

  val updatePassword: ServerEndpoint[Any with AkkaStreams, Future] = {
    ApiErrors
      .withApiErrorVariants(
        antiCsrfWithRequiredSession(sc, gt, checkMode)
      )
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
            case other              => Left(resultToApiError(other))
          }
        }
      )
  }

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
