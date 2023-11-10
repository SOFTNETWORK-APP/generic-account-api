package app.softnetwork.account.persistence.typed

import _root_.akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.persistence.typed.scaladsl.Effect
import app.softnetwork.security.{sha256, Sha512Encryption}
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.Logger
import app.softnetwork.persistence.typed._
import app.softnetwork.account.config.AccountSettings._
import app.softnetwork.account.handlers._
import app.softnetwork.account.message._
import Sha512Encryption._
import akka.cluster.sharding.typed.ShardingEnvelope
import app.softnetwork.notification.api.NotificationClient
import app.softnetwork.notification.config.NotificationSettings
import app.softnetwork.account.model._
import app.softnetwork.persistence._
import app.softnetwork.account.config.{AccountSettings, Password}
import app.softnetwork.validation.{EmailValidator, GsmValidator}
import app.softnetwork.notification.message.{
  ExternalEntityToNotificationEvent,
  NotificationCommandEvent
}
import app.softnetwork.scheduler.message.SchedulerEvents.ExternalSchedulerEvent
import app.softnetwork.scheduler.model.Schedule

import java.time.Instant
import scala.language.{implicitConversions, postfixOps}
import scala.reflect.ClassTag

trait AccountBehavior[T <: Account with AccountDecorator, P <: Profile]
    extends EntityBehavior[AccountCommand, T, ExternalSchedulerEvent, AccountCommandResult]
    with AccountNotifications[T] { self: Generator =>

  def accountKeyDao: AccountKeyDao = AccountKeyDao

  var notificationClient: NotificationClient = _

  protected val generator: Generator = this

  protected val rules: Password.PasswordRules = passwordRules()

  protected def createAccount(entityId: String, cmd: SignUp)(implicit
    context: ActorContext[AccountCommand]
  ): Option[T]

  protected def createProfileUpdatedEvent(
    uuid: String,
    profile: P,
    loginUpdated: Option[Boolean] = None
  )(implicit context: ActorContext[AccountCommand]): ProfileUpdatedEvent[P]

  protected def createAccountCreatedEvent(account: T)(implicit
    context: ActorContext[AccountCommand]
  ): AccountCreatedEvent[T]

  /** @return
    *   node role required to start this actor
    */
  override def role: String = AccountSettings.AkkaNodeRole

  override protected def tagEvent(
    entityId: String,
    event: ExternalSchedulerEvent
  ): Set[String] = {
    event match {
      case _: NotificationCommandEvent =>
        Set(
          NotificationSettings.NotificationConfig.eventStreams.externalToNotificationTag
        )
      case _: AccountCreatedEvent[_] => Set(persistenceId, s"$persistenceId-created")
      case _: AccountActivatedEvent  => Set(persistenceId, s"$persistenceId-activated")
      case _: AccountDisabledEvent   => Set(persistenceId, s"$persistenceId-disabled")
      case _: AccountDeletedEvent    => Set(persistenceId, s"$persistenceId-deleted")
      case _: AccountDestroyedEvent  => Set(persistenceId, s"$persistenceId-destroyed")
      case _: ProfileUpdatedEvent[_] => Set(persistenceId, s"$persistenceId-profile-updated")
      case _: LoginUpdatedEvent      => Set(persistenceId, s"$persistenceId-login-updated")
      case _: InternalAccountEvent   => Set(persistenceId, s"$persistenceId-to-internal")
      case _                         => Set(persistenceId)
    }
  }

  override def init(system: ActorSystem[_], maybeRole: Option[String] = None)(implicit
    tTag: ClassTag[AccountCommand]
  ): ActorRef[ShardingEnvelope[AccountCommand]] = {
    AccountKeyBehavior.init(system, maybeRole)
    notificationClient = NotificationClient(system)
    super.init(system, maybeRole)
  }

  /** @param entityId
    *   - entity identity
    * @param state
    *   - current state
    * @param command
    *   - command to handle
    * @param replyTo
    *   - optional actor to reply to
    * @return
    *   effect
    */
  override def handleCommand(
    entityId: String,
    state: Option[T],
    command: AccountCommand,
    replyTo: Option[ActorRef[AccountCommandResult]],
    timers: TimerScheduler[AccountCommand]
  )(implicit
    context: ActorContext[AccountCommand]
  ): Effect[ExternalSchedulerEvent, Option[T]] = {
    implicit val log: Logger = context.log
    implicit val system: ActorSystem[Nothing] = context.system
    command match {

      case cmd: InitAdminAccount =>
        import cmd._
        rules.validate(password) match {
          case Left(errorCodes) => Effect.none.thenRun(_ => InvalidPassword(errorCodes) ~> replyTo)
          case Right(success) if success =>
            state match {
              case Some(account) =>
                Effect
                  .persist(
                    PasswordUpdatedEvent(
                      entityId,
                      encrypt(password),
                      account.verificationCode,
                      account.verificationToken
                    )
                  )
                  .thenRun(_ => AdminAccountInitialized ~> replyTo)
              case _ =>
                createAccount(entityId, cmd) match {
                  case Some(account) =>
                    import account._
                    if (
                      !secondaryPrincipals
                        .exists(principal => lookupAccount(principal.value).isDefined)
                    ) {
                      Effect
                        .persist(
                          createAccountCreatedEvent(account)
                        )
                        .thenRun(_ => AdminAccountInitialized ~> replyTo)
                    } else {
                      Effect.none.thenRun(_ => LoginAlreadyExists ~> replyTo)
                    }
                  case _ => Effect.none.thenRun(_ => LoginUnaccepted ~> replyTo)
                }
            }
        }

      /** handle signUp * */
      case cmd: SignUp =>
        state match {
          case Some(account) if !account.anonymous.getOrElse(false) =>
            Effect.none.thenRun(_ => AccountAlreadyExists ~> replyTo)
          case _ =>
            import cmd._
            if (confirmPassword.isDefined && !password.equals(confirmPassword.get)) {
              Effect.none.thenRun(_ => PasswordsNotMatched ~> replyTo) //.thenStop()
            } else {
              rules.validate(password) match {
                case Left(errorCodes) =>
                  Effect.none.thenRun(_ => InvalidPassword(errorCodes) ~> replyTo)
                case Right(success) if success =>
                  createAccount(entityId, cmd) match {
                    case Some(account) =>
                      import account._
                      if (
                        !secondaryPrincipals
                          .exists(principal => lookupAccount(principal.value).isDefined)
                      ) {
                        val activationRequired = status.isInactive && notificationsEnabled
                        val updatedAccount =
                          if (activationRequired) { // an activation is required
                            log.info(s"activation required for ${account.primaryPrincipal.value}")
                            val activationToken = generator.generateToken(
                              account.primaryPrincipal.value,
                              ActivationTokenExpirationTime
                            )
                            accountKeyDao.addAccountKey(activationToken.token, entityId)
                            account
                              .copyWithVerificationToken(Some(activationToken))
                              .copyWithFromAnonymous(state.flatMap(_.anonymous).getOrElse(false))
                              .copyWithAnonymous(false)
                              .asInstanceOf[T]
                          } else {
                            account
                              .copyWithFromAnonymous(state.flatMap(_.anonymous).getOrElse(false))
                              .copyWithAnonymous(false)
                              .asInstanceOf[T]
                          }
                        val notifications: Seq[ExternalEntityToNotificationEvent] = {
                          updatedAccount.verificationToken match {
                            case Some(verificationToken) =>
                              sendActivation(entityId, account, verificationToken)
                            case _ =>
                              if (updatedAccount.status.isActive) {
                                sendRegistration(entityId, updatedAccount)
                              } else {
                                Seq.empty
                              }
                          }
                        }
                        Effect
                          .persist(
                            List(createAccountCreatedEvent(updatedAccount)) ++ notifications.toList
                          )
                          .thenRun(_ => AccountCreated(updatedAccount) ~> replyTo)
                      } else {
                        Effect.none.thenRun(_ => LoginAlreadyExists ~> replyTo)
                      }
                    case _ => Effect.none.thenRun(_ => LoginUnaccepted ~> replyTo)
                  }
              }
            }
        }

      case SignUpAnonymous =>
        state match {
          case Some(_) =>
            Effect.none.thenRun(_ => AccountAlreadyExists ~> replyTo)
          case _ =>
            createAccount(
              entityId,
              new SignUp {
                override def login: String = entityId
                override def password: String = AnonymousPassword
              }
            ) match {
              case Some(account) =>
                import account._
                if (
                  !secondaryPrincipals.exists(principal => lookupAccount(principal.value).isDefined)
                ) {
                  val updatedAccount = account.copyWithAnonymous(true).asInstanceOf[T]
                  Effect
                    .persist(
                      createAccountCreatedEvent(updatedAccount)
                    )
                    .thenRun(_ => AccountCreated(updatedAccount) ~> replyTo)
                } else {
                  Effect.none.thenRun(_ => LoginAlreadyExists ~> replyTo)
                }
              case _ => Effect.none.thenRun(_ => LoginUnaccepted ~> replyTo)
            }
        }

      /** handle account activation * */
      case cmd: Activate =>
        import cmd._
        state match {
          case Some(account) if account.status.isInactive =>
            import account._
            verificationToken match {
              case Some(v) =>
                if (v.expired) {
                  accountKeyDao.removeAccountKey(v.token)
                  val activationToken = generator.generateToken(
                    account.primaryPrincipal.value,
                    ActivationTokenExpirationTime
                  )
                  accountKeyDao.addAccountKey(activationToken.token, entityId)
                  val notifications = sendActivation(entityId, account, activationToken)
                  Effect
                    .persist(
                      List(
                        VerificationTokenAdded(
                          entityId,
                          activationToken
                        )
                      ) ++ notifications.toList
                    )
                    .thenRun(_ => TokenExpired ~> replyTo)
                } else if (v.token != token) {
                  Effect.none.thenRun(_ => InvalidToken ~> replyTo)
                } else {
                  Effect
                    .persist(
                      AccountActivatedEvent(entityId).withLastUpdated(Instant.now())
                    )
                    .thenRun(state => AccountActivated(state.getOrElse(account)) ~> replyTo)
                }
              case _ => Effect.none.thenRun(_ => TokenNotFound ~> replyTo)
            }
          case None => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
          case _    => Effect.none.thenRun(_ => IllegalStateError ~> replyTo)
        }

      case cmd: BasicAuth =>
        import cmd._
        authenticate(
          credentials.identifier,
          None,
          encrypted => credentials.verify(encrypted, Sha512Encryption.hash(encrypted)),
          entityId,
          state,
          replyTo
        )

      case cmd: GenerateAuthorizationCode => // response_type=code
        state match {
          case Some(account) if account.status.isActive =>
            import cmd._
            account.applications.find(_.clientId == clientId) match {
//              case Some(application) if application.authorizationCode.exists(!_.expired) =>
//                Effect.none.thenRun(_ => AuthorizationCodeAlreadyExists ~> replyTo)
              case _ =>
                val authorizationCode =
                  generator.generateAuthorizationCode(
                    clientId,
                    scope,
                    redirectUri,
                    None
                  )
                accountKeyDao.addAccountKey(authorizationCode.code, entityId)
                val application: Application = account.applications
                  .find(_.clientId == clientId)
                  .getOrElse(
                    Application.defaultInstance
                      .withClientId(clientId)
                      .copy(redirectUri = redirectUri)
                  )
                  .withAuthorizationCode(
                    authorizationCode.copy(code = sha256(authorizationCode.code))
                  )
                Effect
                  .persist(
                    ApplicationsUpdatedEvent(
                      entityId,
                      Instant.now(),
                      account.applications.filterNot(_.clientId == clientId) :+ application
                    )
                  )
                  .thenRun(_ => AuthorizationCodeGenerated(authorizationCode) ~> replyTo)
            }

          case Some(account) if account.status.isDisabled =>
            Effect.none.thenRun(_ => AccountDisabled ~> replyTo)

          case Some(account) if account.status.isDeleted =>
            Effect.none.thenRun(_ => AccountDeleted(account) ~> replyTo)

          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: GenerateAccessToken => // grant_type=authorization_code
        state match {
          case Some(account) if account.status.isActive =>
            import cmd._
            account.applications.find(_.clientId == clientId) match {
              case Some(application) if application.accessToken.exists(!_.expired) =>
                Effect.none.thenRun(_ => AccessTokenAlreadyExists ~> replyTo)

              case Some(application) =>
                application.authorizationCode match {
                  case Some(authorizationCode) if authorizationCode.expired =>
                    Effect.none.thenRun(_ => CodeExpired ~> replyTo)

                  case Some(authorizationCode) if authorizationCode.code != sha256(code) =>
                    // an authorization code exists but does not match
                    // it may be a replay attack
                    // the authorization code should be considered corrupted and thus removed
                    accountKeyDao.removeAccountKey(code)
                    accountKeyDao.removeAccountKey(authorizationCode.code)
                    Effect
                      .persist(
                        ApplicationsUpdatedEvent(
                          entityId,
                          Instant.now(),
                          account.applications.filterNot(_.clientId == clientId) :+ application
                            .copy(authorizationCode = None)
                        )
                      )
                      .thenRun(_ => InvalidCode ~> replyTo)

                  case Some(authorizationCode)
                      if authorizationCode.redirectUri
                        .getOrElse("") != cmd.redirectUri.getOrElse("") =>
                    // an authorization code exists but redirect uri does not match
                    // it may be a replay attack
                    // the authorization code should be considered corrupted and thus removed
                    accountKeyDao.removeAccountKey(authorizationCode.code)
                    Effect
                      .persist(
                        ApplicationsUpdatedEvent(
                          entityId,
                          Instant.now(),
                          account.applications.filterNot(_.clientId == clientId) :+ application
                            .copy(authorizationCode = None)
                        )
                      )
                      .thenRun(_ => InvalidRedirection ~> replyTo)

                  case Some(authorizationCode) =>
                    val accessToken =
                      generator.generateAccessToken(
                        account.primaryPrincipal.value,
                        authorizationCode.scope
                      )
                    accountKeyDao.removeAccountKey(authorizationCode.code)
                    accountKeyDao.addAccountKey(accessToken.token, entityId)
                    accountKeyDao.addAccountKey(accessToken.refreshToken, entityId)
                    Effect
                      .persist(
                        ApplicationsUpdatedEvent(
                          entityId,
                          Instant.now(),
                          account.applications.filterNot(_.clientId == clientId) :+ application
                            .withAccessToken(
                              accessToken.copy(
                                token = sha256(accessToken.token),
                                refreshToken = sha256(accessToken.refreshToken)
                              )
                            )
                            .copy(authorizationCode = None)
                        )
                      )
                      .thenRun(_ => AccessTokenGenerated(accessToken) ~> replyTo)

                  case _ =>
                    accountKeyDao.removeAccountKey(code)
                    Effect.none.thenRun(_ => CodeNotFound ~> replyTo)
                }

              case _ =>
                accountKeyDao.removeAccountKey(code)
                Effect.none.thenRun(_ => ApplicationNotFound ~> replyTo)
            }

          case Some(account) if account.status.isDisabled =>
            Effect.none.thenRun(_ => AccountDisabled ~> replyTo)

          case Some(account) if account.status.isDeleted =>
            Effect.none.thenRun(_ => AccountDeleted(account) ~> replyTo)

          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: RefreshAccessToken => // grant_type=refresh_token
        state match {
          case Some(account) if account.status.isActive =>
            import cmd._
            account.applications.find(
              _.accessToken.map(_.refreshToken).getOrElse("") == sha256(refreshToken)
            ) match {
              case Some(application) =>
                val accessToken =
                  generator.generateAccessToken(
                    account.primaryPrincipal.value,
                    application.accessToken.flatMap(_.scope)
                  )
                accountKeyDao.addAccountKey(accessToken.token, entityId)
                accountKeyDao.addAccountKey(accessToken.refreshToken, entityId)
                Effect
                  .persist(
                    ApplicationsUpdatedEvent(
                      entityId,
                      Instant.now(),
                      account.applications
                        .filterNot(_.clientId == application.clientId) :+ application
                        .withAccessToken(
                          accessToken.copy(
                            token = sha256(accessToken.token),
                            refreshToken = sha256(accessToken.refreshToken)
                          )
                        )
                    )
                  )
                  .thenRun(_ => AccessTokenRefreshed(accessToken) ~> replyTo)

              case _ =>
                accountKeyDao.removeAccountKey(refreshToken)
                Effect.none.thenRun(_ => ApplicationNotFound ~> replyTo)
            }

          case Some(account) if account.status.isDisabled =>
            Effect.none.thenRun(_ => AccountDisabled ~> replyTo)

          case Some(account) if account.status.isDeleted =>
            Effect.none.thenRun(_ => AccountDeleted(account) ~> replyTo)

          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: OAuth =>
        state match {
          case Some(account) if account.status.isActive =>
            import cmd._
            account.applications.find(
              _.accessToken.map(_.token).getOrElse("") == sha256(token)
            ) match {
              case Some(application) =>
                application.accessToken match {
                  case Some(accessToken) if accessToken.expired =>
                    Effect.none.thenRun(_ => TokenExpired ~> replyTo)

                  case Some(_) =>
                    Effect
                      .persist(
                        loginSucceeded(entityId, state) :+
                        LoginSucceeded(
                          entityId,
                          Instant.now(),
                          None
                        )
                      )
                      .thenRun(state => LoginSucceededResult(state.get) ~> replyTo)

                  case _ =>
                    accountKeyDao.removeAccountKey(token)
                    Effect.none.thenRun(_ => TokenNotFound ~> replyTo)
                }

              case _ =>
                accountKeyDao.removeAccountKey(token)
                Effect.none.thenRun(_ => ApplicationNotFound ~> replyTo)
            }

          case Some(account) if account.status.isDisabled =>
            Effect.none.thenRun(_ => AccountDisabled ~> replyTo)

          case Some(account) if account.status.isDeleted =>
            Effect.none.thenRun(_ => AccountDeleted(account) ~> replyTo)

          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      /** handle login * */
      case cmd: Login =>
        import cmd._
        authenticate(
          login,
          anonymous,
          encrypted => checkEncryption(encrypted, password),
          entityId,
          state,
          replyTo
        )

      /** handle send verification code * */
      case cmd: SendVerificationCode =>
        import cmd._
        if (EmailValidator.check(principal) || GsmValidator.check(principal)) {
          state match {
            case Some(account) if account.principals.exists(_.value == principal) =>
              account.verificationCode.foreach(v => accountKeyDao.removeAccountKey(v.code))
              val verificationCode =
                generator.generatePinCode(VerificationCodeSize, VerificationCodeExpirationTime)
              accountKeyDao.addAccountKey(verificationCode.code, entityId)
              val notifications = sendVerificationCode(generateUUID(), account, verificationCode)
              Effect
                .persist(
                  List(
                    VerificationCodeAdded(
                      entityId,
                      verificationCode
                    ).withLastUpdated(Instant.now())
                  ) ++ notifications.toList
                )
                .thenRun(_ => VerificationCodeSent ~> replyTo)
            case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
          }
        } else {
          Effect.none.thenRun(_ => InvalidPrincipal ~> replyTo)
        }

      case cmd: SendResetPasswordToken =>
        import cmd._
        if (EmailValidator.check(principal) || GsmValidator.check(principal)) {
          state match {
            case Some(account) if account.principals.exists(_.value == principal) =>
              account.verificationToken.foreach(v => accountKeyDao.removeAccountKey(v.token))
              val verificationToken = generator.generateToken(
                account.primaryPrincipal.value,
                VerificationTokenExpirationTime
              )
              accountKeyDao.addAccountKey(verificationToken.token, entityId)
              val notifications = sendResetPassword(generateUUID(), account, verificationToken)
              Effect
                .persist(
                  List(
                    VerificationTokenAdded(
                      entityId,
                      verificationToken
                    ).withLastUpdated(Instant.now())
                  ) ++ notifications.toList
                )
                .thenRun(_ => ResetPasswordTokenSent ~> replyTo)
            case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
          }
        } else {
          Effect.none.thenRun(_ => InvalidPrincipal ~> replyTo)
        }

      case cmd: CheckResetPasswordToken =>
        import cmd._
        state match {
          case Some(account) =>
            import account._
            verificationToken match {
              case Some(v) =>
                if (v.expired) {
                  if (RegenerationOfThePasswordResetToken) {
                    accountKeyDao.removeAccountKey(v.token)
                    val verificationToken = generator.generateToken(
                      account.primaryPrincipal.value,
                      ActivationTokenExpirationTime
                    )
                    accountKeyDao.addAccountKey(verificationToken.token, entityId)
                    val notifications =
                      sendResetPassword(generateUUID(), account, verificationToken)
                    Effect
                      .persist(
                        List(
                          VerificationTokenAdded(
                            entityId,
                            verificationToken
                          ).withLastUpdated(Instant.now())
                        ) ++ notifications.toList
                      )
                      .thenRun(_ => NewResetPasswordTokenSent ~> replyTo)
                  } else {
                    Effect.none.thenRun(_ => TokenExpired ~> replyTo)
                  }
                } else {
                  if (v.token != token) {
                    log.warn(s"tokens do not match !!!!!")
                  }
                  Effect.none.thenRun(_ => ResetPasswordTokenChecked ~> replyTo)
                }
              case _ => Effect.none.thenRun(_ => TokenNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: ResetPassword =>
        import cmd._
        if (!newPassword.equals(confirmedPassword.getOrElse(newPassword))) {
          Effect.none.thenRun(_ => PasswordsNotMatched ~> replyTo)
        } else {
          rules.validate(newPassword) match {
            case Left(errorCodes) =>
              Effect.none.thenRun(_ => InvalidPassword(errorCodes) ~> replyTo)
            case Right(success) if success =>
              state match {
                case Some(account) =>
                  import account._
                  if (NotificationsConfig.resetPasswordCode) {
                    verificationCode match {
                      case Some(verification) =>
                        if (!verification.expired) {
                          Effect
                            .persist(
                              PasswordUpdatedEvent(
                                entityId,
                                encrypt(newPassword),
                                None,
                                account.verificationToken
                              ).withLastUpdated(Instant.now())
                            )
                            .thenRun(_ =>
                              {
                                accountKeyDao.removeAccountKey(verification.code)
                                PasswordReseted(entityId)
                              } ~> replyTo
                            )
                        } else {
                          Effect.none.thenRun(_ => CodeExpired ~> replyTo)
                        }
                      case _ => Effect.none.thenRun(_ => CodeNotFound ~> replyTo)
                    }
                  } else {
                    verificationToken match {
                      case Some(verification) =>
                        if (!verification.expired) {
                          Effect
                            .persist(
                              PasswordUpdatedEvent(
                                entityId,
                                encrypt(newPassword),
                                account.verificationCode,
                                None
                              ).withLastUpdated(Instant.now())
                            )
                            .thenRun(_ =>
                              {
                                accountKeyDao.removeAccountKey(verification.token)
                                PasswordReseted(entityId)
                              } ~> replyTo
                            )
                        } else {
                          Effect.none.thenRun(_ => TokenExpired ~> replyTo)
                        }
                      case _ => Effect.none.thenRun(_ => TokenNotFound ~> replyTo)
                    }
                  }
                case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
              }
          }
        }

      /** handle update password * */
      case cmd: UpdatePassword =>
        import cmd._
        if (!newPassword.equals(confirmedPassword.getOrElse(newPassword))) {
          Effect.none.thenRun(_ => PasswordsNotMatched ~> replyTo)
        } else {
          rules.validate(newPassword) match {
            case Left(errorCodes) =>
              Effect.none.thenRun(_ => InvalidPassword(errorCodes) ~> replyTo)
            case Right(success) if success =>
              state match {
                case Some(account) =>
                  import account._
                  if (checkEncryption(credentials, oldPassword)) {
                    val notifications = sendPasswordUpdated(generateUUID(), account)
                    Effect
                      .persist(
                        List(
                          PasswordUpdatedEvent(
                            entityId,
                            encrypt(newPassword),
                            account.verificationCode,
                            account.verificationToken
                          ).withLastUpdated(Instant.now())
                        ) ++ notifications.toList
                      )
                      .thenRun(state => PasswordUpdated(state.get) ~> replyTo)
                  } else {
                    Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo)
                  }
                case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
              }
          }
        }

      /** handle device registration
        */
      case cmd: RegisterDevice =>
        import cmd._
        state match {
          case Some(_) if entityId == uuid =>
            Effect
              .persist(
                DeviceRegisteredEvent(
                  entityId,
                  registration
                ).withLastUpdated(Instant.now())
              )
              .thenRun(_ => DeviceRegistered ~> replyTo)
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      /** handle device unregistration
        */
      case cmd: UnregisterDevice =>
        import cmd._
        state match {
          case Some(account) if entityId == uuid =>
            account.registrations.find(_.regId == regId) match {
              case Some(r) =>
                Effect
                  .persist(
                    DeviceUnregisteredEvent(
                      entityId,
                      r
                    ).withLastUpdated(Instant.now())
                  )
                  .thenRun(_ => DeviceUnregistered ~> replyTo)
              case _ => Effect.none.thenRun(_ => DeviceRegistrationNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => DeviceRegistrationNotFound ~> replyTo)
        }

      /** handle unsubscribe * */
      case _: Unsubscribe =>
        state match {
          case Some(_) =>
            // TODO remove all account keys
            Effect
              .persist(
                accountUnsubscribed(entityId, state) :+ AccountDeletedEvent(entityId)
                  .withLastUpdated(Instant.now())
              )
              .thenRun(state => AccountDeleted(state.get) ~> replyTo)
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case _: DestroyAccount.type =>
        state match {
          case Some(_) =>
            Effect
              .persist(
                accountDestroyed(entityId, state) :+ AccountDestroyedEvent(entityId)
                  .withLastUpdated(Instant.now())
              )
              .thenRun(_ => AccountDestroyed(entityId) ~> replyTo)
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case _: Logout.type =>
        Effect
          .persist(logoutSucceeded(entityId, state) :+ LogoutEvent(entityId, Instant.now()))
          .thenRun(_ => LogoutSucceeded ~> replyTo)

      case cmd: UpdateLogin =>
        import cmd._
        state match {
          case Some(account) =>
            val principal = Principal(newLogin.trim)
            if (!account.principals.exists(_.value == oldLogin)) { //check login against account principals
              Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo)
            } else if (!checkEncryption(account.credentials, password)) {
              Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo)
            } else if (lookupAccount(newLogin.trim).getOrElse(entityId) != entityId) {
              principal.`type` match {
                case PrincipalType.Gsm =>
                  Effect.none.thenRun(_ => GsmAlreadyExists ~> replyTo)
                case PrincipalType.Email =>
                  Effect.none.thenRun(_ => EmailAlreadyExists ~> replyTo)
                case PrincipalType.Username =>
                  Effect.none.thenRun(_ => UsernameAlreadyExists ~> replyTo)
                case _ =>
                  Effect.none.thenRun(_ => LoginNotUpdated ~> replyTo)
              }
            } else {
              val notifications = sendPrincipalUpdated(generateUUID(), account)
              Effect
                .persist(
                  List(
                    LoginUpdatedEvent(
                      uuid = entityId,
                      principal = principal
                    ).withLastUpdated(Instant.now())
                  ) ++
                  account.profiles.map(kv =>
                    createProfileUpdatedEvent(
                      entityId,
                      kv._2.copyWithPrincipal(principal).asInstanceOf[P],
                      Some(true)
                    )
                  ) ++ notifications.toList
                )
                .thenRun(_ => LoginUpdated ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: UpdateProfile =>
        import cmd._
        state match {
          case Some(account) =>
            val phoneNumber = profile.phoneNumber.getOrElse("").trim
            val email = profile.email.getOrElse("").trim
            if (
              VerificationGsmEnabled &&
              phoneNumber.nonEmpty &&
              lookupAccount(phoneNumber).getOrElse(entityId) != entityId
            ) {
              Effect.none.thenRun(_ => GsmAlreadyExists ~> replyTo)
            } else if (
              VerificationEmailEnabled &&
              email.nonEmpty &&
              lookupAccount(email).getOrElse(entityId) != entityId
            ) {
              Effect.none.thenRun(_ => EmailAlreadyExists ~> replyTo)
            } else {
              Effect
                .persist(
                  createProfileUpdatedEvent(
                    entityId,
                    account.completeProfile(profile).asInstanceOf[P],
                    Some(false)
                  )
                )
                .thenRun(_ =>
                  {
                    ProfileUpdated
                  } ~> replyTo
                )
            }
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: SwitchProfile =>
        import cmd._
        state match {
          case Some(account) =>
            Effect
              .persist(
                ProfileSwitchedEvent(
                  entityId,
                  name
                ).withLastUpdated(Instant.now())
              )
              .thenRun(_ =>
                {
                  ProfileSwitched(account.profile(Some(name)))
                } ~> replyTo
              )
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: LoadProfile =>
        import cmd._
        state match {
          case Some(account) =>
            account.profile(name) match {
              case Some(profile) => Effect.none.thenRun(_ => ProfileLoaded(profile) ~> replyTo)
              case _             => Effect.none.thenRun(_ => ProfileNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: RecordNotification =>
        import cmd._
        Effect
          .persist(
            uuids
              .map(
                AccountNotificationRecordedEvent(
                  _,
                  channel,
                  subject,
                  content
                )
              )
              .toList
          )
          .thenRun(_ => NotificationRecorded ~> replyTo)

      case cmd: WrapInternalAccountEvent =>
        import cmd._
        state match {
          case Some(account) =>
            event match {
              case evt: AccountNotificationRecordedEvent =>
                import evt._
                val notificationUuid = generateUUID()
                val notifications = addNotifications(
                  notificationUuid,
                  account,
                  subject,
                  if (channel.isMailType)
                    StringEscapeUtils.escapeHtml4(content).replaceAll("\\\n", "<br/>")
                  else
                    StringEscapeUtils.unescapeHtml4(content).replaceAll("<br/>", "\\\n"),
                  Seq(channel)
                )
                Effect
                  .persist(
                    notifications.toList
                  )
                  .thenRun(_ => AccountNotificationSent(entityId, notificationUuid) ~> replyTo)
              case _ => Effect.none.thenRun(_ => InternalAccountEventNotHandled ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: TriggerSchedule4Account =>
        Effect.none.thenRun(_ =>
          {
            scheduleTriggered(cmd.schedule, entityId, state) match {
              case Left(l)  => l
              case Right(r) => r
            }
          } ~> replyTo
        )

      /** no handlers * */
      case _ => super.handleCommand(entityId, state, command, replyTo, timers)

    }
  }

  /** @param entityId
    *   - entity id
    * @param state
    *   - entity state
    * @param context
    *   - context
    * @return
    *   additional events to persist after account has successfully logged in
    */
  protected def loginSucceeded(entityId: String, state: Option[T])(implicit
    context: ActorContext[AccountCommand]
  ): List[ExternalSchedulerEvent] = List.empty

  /** @param entityId
    *   - entity id
    * @param state
    *   - entity state
    * @param context
    *   - context
    * @return
    *   additional events to persist after account has successfully logged out
    */
  protected def logoutSucceeded(entityId: String, state: Option[T])(implicit
    context: ActorContext[AccountCommand]
  ): List[ExternalSchedulerEvent] = List.empty

  /** @param entityId
    *   - entity id
    * @param state
    *   - entity state
    * @param context
    *   - context
    * @return
    *   additional events to persist after account has successfully unsubscribed
    */
  protected def accountUnsubscribed(entityId: String, state: Option[T])(implicit
    context: ActorContext[AccountCommand]
  ): List[ExternalSchedulerEvent] = List.empty

  /** @param entityId
    *   - entity id
    * @param state
    *   - entity state
    * @param context
    *   - context
    * @return
    *   additional events to persist while account is about to be destroyed
    */
  protected def accountDestroyed(entityId: String, state: Option[T])(implicit
    context: ActorContext[AccountCommand]
  ): List[ExternalSchedulerEvent] = List.empty

  /** @param schedule
    *   - the schedule that has been triggered
    * @param entityId
    *   - entity id for which the schedule has been triggered
    * @param state
    *   - entity state
    * @param context
    *   - context
    * @return
    *   whether the schedule has been successfully triggered or not
    */
  protected def scheduleTriggered(schedule: Schedule, entityId: String, state: Option[T])(implicit
    context: ActorContext[AccountCommand]
  ): Either[Schedule4AccountNotTriggered.type, Schedule4AccountTriggered] = Left(
    Schedule4AccountNotTriggered
  )

  protected def authenticate(
    login: String,
    anonymous: Option[String],
    verify: String => Boolean,
    entityId: String,
    state: Option[T],
    replyTo: Option[ActorRef[AccountCommandResult]]
  )(implicit context: ActorContext[AccountCommand]): Effect[ExternalSchedulerEvent, Option[T]] = {
    implicit val log: Logger = context.log
    implicit val system: ActorSystem[_] = context.system
    state match {
      case Some(account) if account.status.isActive || account.status.isDeleted =>
        val checkLogin =
          account.principals.exists(_.value == login) //check login against account principal
        if (checkLogin && verify(account.credentials)) {
          Effect
            .persist(
              loginSucceeded(entityId, state) :+
              LoginSucceeded(
                entityId,
                Instant.now(),
                anonymous
              )
            )
            .thenRun(state => LoginSucceededResult(state.get) ~> replyTo)
        } else if (!checkLogin) {
          Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo)
        } else { // wrong password
          val nbLoginFailures = account.nbLoginFailures + 1
          val disabled = nbLoginFailures > maxLoginFailures // disable account
          if (disabled) {
            account.applications.foreach(application => {
              application.authorizationCode.foreach(code => {
                accountKeyDao.removeAccountKey(code.code)
              })
              application.accessToken.foreach(token => {
                accountKeyDao.removeAccountKey(token.token)
                accountKeyDao.removeAccountKey(token.refreshToken)
              })
            })
          }
          val notifications: Seq[ExternalEntityToNotificationEvent] =
            if (disabled && !account.status.isDisabled) {
              sendAccountDisabled(generateUUID(), account)
            } else {
              Seq.empty
            }
          Effect
            .persist(
              (if (disabled)
                 List(
                   AccountDisabledEvent(
                     entityId,
                     nbLoginFailures
                   ).withLastUpdated(Instant.now()),
                   ApplicationsUpdatedEvent(
                     entityId,
                     Instant.now(),
                     account.applications.map(
                       _.copy(
                         authorizationCode = None,
                         accessToken = None
                       )
                     )
                   )
                 )
               else {
                 List(
                   LoginFailed(
                     entityId,
                     nbLoginFailures
                   ).withLastUpdated(Instant.now())
                 )
               }) ++ notifications.toList
            )
            .thenRun(_ =>
              {
                if (disabled) {
                  if (!account.status.isDisabled) {
                    log.info(s"reset password required for ${account.primaryPrincipal.value}")
                  }
                  AccountDisabled
                } else {
                  log.info(
                    s"$nbLoginFailures login failure(s) for ${account.primaryPrincipal.value}"
                  )
                  LoginAndPasswordNotMatched
                }
              } ~> replyTo
            )
        }
      case Some(account) if account.status.isDisabled =>
        log.info(s"reset password required for ${account.primaryPrincipal.value}")
        Effect
          .persist(sendAccountDisabled(generateUUID(), account).toList)
          .thenRun(_ => AccountDisabled ~> replyTo)
      case None => Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo) //WrongLogin
      case _    => Effect.none.thenRun(_ => IllegalStateError ~> replyTo)
    }
  }

  /** @param state
    *   - current state
    * @param event
    *   - event to hanlde
    * @return
    *   new state
    */
  override def handleEvent(state: Option[T], event: ExternalSchedulerEvent)(implicit
    context: ActorContext[_]
  ): Option[T] = {
    implicit val system: ActorSystem[Nothing] = context.system
    event match {
      case evt: AccountCreatedEvent[_] =>
        val account = evt.document
        account.secondaryPrincipals.foreach(principal =>
          accountKeyDao.addAccountKey(principal.value, account.uuid)
        )
        Some(account.asInstanceOf[T])

      case evt: AccountActivatedEvent =>
        state.map(
          _.copyWithStatus(AccountStatus.Active)
            .copyWithVerificationToken(None)
            .copyWithLastUpdated(evt.lastUpdated)
            .asInstanceOf[T]
        )

      case evt: AccountDisabledEvent =>
        import evt._
        state.map(
          _.copyWithStatus(AccountStatus.Disabled)
            .copyWithNbLoginFailures(nbLoginFailures)
            .copyWithLastUpdated(lastUpdated)
            .asInstanceOf[T]
        )

      case evt: AccountDeletedEvent =>
        state.map(
          _.copyWithStatus(AccountStatus.Deleted)
            .copyWithLastUpdated(evt.lastUpdated)
            .asInstanceOf[T]
        )

      case _: AccountDestroyedEvent =>
        state match {
          case Some(account) =>
            account.verificationCode.foreach(v => accountKeyDao.removeAccountKey(v.code))
            account.verificationToken.foreach(v => accountKeyDao.removeAccountKey(v.token))
            account.principals.foreach(principal => accountKeyDao.removeAccountKey(principal.value))
            account.secondaryPrincipals.foreach(principal =>
              accountKeyDao.removeAccountKey(principal.value)
            )
          case _ =>
        }
        emptyState

      case evt: LoginUpdatedEvent =>
        import evt._
        state match {
          case Some(account) =>
            account.secondaryPrincipals.foreach(principal =>
              accountKeyDao.removeAccountKey(principal.value)
            )
            val updatedAccount = account.add(principal)
            updatedAccount.secondaryPrincipals.foreach(principal =>
              accountKeyDao.addAccountKey(principal.value, uuid)
            )
            Some(
              updatedAccount
                .copyWithLastUpdated(lastUpdated)
                .asInstanceOf[T]
            )
          case _ => state
        }

      case evt: ProfileUpdatedEvent[_] =>
        import evt._
        state match {
          case Some(account) =>
            account.secondaryPrincipals.foreach(principal =>
              accountKeyDao.removeAccountKey(principal.value)
            )
            val updatedAccount = account.add(profile)
            updatedAccount.secondaryPrincipals.foreach(principal =>
              accountKeyDao.addAccountKey(principal.value, uuid)
            )
            Some(
              updatedAccount
                .copyWithLastUpdated(lastUpdated)
                .asInstanceOf[T]
            )
          case _ => state
        }

      case evt: DeviceRegisteredEvent =>
        import evt._
        state.map(account =>
          account
            .copyWithRegistrations(
              account.registrations
                .filterNot(reg =>
                  reg.deviceId.isDefined && reg.deviceId.getOrElse("") == registration.deviceId
                    .getOrElse("")
                )
                .filterNot(_.regId == registration.regId)
                .+:(registration)
            )
            .copyWithLastUpdated(lastUpdated)
            .asInstanceOf[T]
        )

      case evt: DeviceUnregisteredEvent =>
        import evt._
        state.map(account =>
          account
            .copyWithRegistrations(
              account.registrations.filterNot(_.regId == registration.regId)
            )
            .copyWithLastUpdated(lastUpdated)
            .asInstanceOf[T]
        )

      case evt: VerificationTokenAdded =>
        import evt._
        state.map(
          _.copyWithVerificationToken(Some(token))
            .copyWithLastUpdated(lastUpdated)
            .asInstanceOf[T]
        )

      case evt: VerificationCodeAdded =>
        import evt._
        state.map(
          _.copyWithVerificationCode(Some(code))
            .copyWithLastUpdated(lastUpdated)
            .asInstanceOf[T]
        )

      case evt: ProfileSwitchedEvent =>
        import evt._
        state.map(
          _.setCurrentProfile(name)
            .copyWithLastUpdated(lastUpdated)
            .asInstanceOf[T]
        )

      case evt: LoginSucceeded =>
        import evt._
        state.map(
          _.copyWithNbLoginFailures(0) // reset number of login failures
            .copyWithLastLogin(Some(lastLogin))
            .withLastUpdated(lastLogin)
            .asInstanceOf[T]
        )

      case evt: LoginFailed =>
        import evt._
        state.map(
          _.copyWithNbLoginFailures(nbLoginFailures)
            .copyWithLastUpdated(lastUpdated)
            .asInstanceOf[T]
        )

      case evt: PasswordUpdatedEvent =>
        import evt._
        state.map(
          _.copyWithCredentials(credentials)
            .copyWithVerificationCode(code)
            .copyWithVerificationToken(token)
            .copyWithStatus(AccountStatus.Active) //TODO check this
            .copyWithNbLoginFailures(0)
            .copyWithLastUpdated(lastUpdated)
            .asInstanceOf[T]
        )

      case evt: LogoutEvent =>
        import evt._
        state.map(
          _.withLastLogout(lastLogout)
            .withLastUpdated(lastLogout)
            .asInstanceOf[T]
        )

      case evt: ApplicationsUpdatedEvent =>
        import evt._
        state.map(
          _.withApplications(applications)
            .withLastUpdated(lastUpdated)
            .asInstanceOf[T]
        )

      case _ => super.handleEvent(state, event)
    }
  }

  private[this] def lookupAccount(key: String)(implicit system: ActorSystem[_]): Option[String] =
    accountKeyDao.lookupAccount(key) complete ()
}
