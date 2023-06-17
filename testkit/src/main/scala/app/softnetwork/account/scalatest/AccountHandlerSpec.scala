package app.softnetwork.account.scalatest

import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.handlers.{AccountHandler, Generator}
import app.softnetwork.account.message._
import app.softnetwork.account.model._
import app.softnetwork.persistence._
import app.softnetwork.persistence.typed.CommandTypeKey
import org.scalatest.wordspec.AnyWordSpecLike

/** Created by smanciot on 18/04/2020.
  */
trait AccountHandlerSpec[T <: Account with AccountDecorator, P <: Profile with ProfileDecorator]
    extends AccountHandler
    with AnyWordSpecLike
    with AccountTestKit[T, P] { _: CommandTypeKey[AccountCommand] with Generator =>

  private val anonymous = "anonymous"

  private val username = "test"

  private val username2 = "test2"

  private val firstname = "firstname"

  private def computeEmail(user: String) = s"$user@gmail.com"

  private val email = computeEmail(username)

  private val email2 = computeEmail(username2)

  private val gsm = "33660010203"

  private val gsm2 = "33660020304"

  private val password = "Changeit1"

  private val wrongPassword = "password"

  private val newPassword = "Changeit2"

  private val usernameUuid: String = generateUUID(Some(username))

  private val usernameUuid2: String = generateUUID(Some(username2))

  private val emailUuid: String = generateUUID(Some(email))

  private val emailUuid2: String = generateUUID(Some(email2))

  private val gsmUuid: String = generateUUID(Some(gsm))

  private val gsmUuid2: String = generateUUID(Some(gsm2))

  private val anonymousUuid: String = generateUUID(Some(anonymous))

  private val devices = Seq(iosDevice, androidDevice)

  "Anonymous SignUp" should {
    "work" in {
      this ?? (anonymousUuid, SignUpAnonymous) await {
        case r: AccountCreated =>
          import r._
          account.anonymous.getOrElse(false) shouldBe true
          account.status shouldBe AccountStatus.Active
          account.email.isDefined shouldBe false
          account.gsm.isDefined shouldBe false
          account.username.isDefined shouldBe true
        case other => fail(other.getClass)
      }
    }
    "fail if anonymous account already exists" in {
      this ?? (anonymousUuid, SignUpAnonymous) await {
        case AccountAlreadyExists =>
        case other                => fail(other.getClass)
      }
    }
  }

  "SignUp" should {
    "fail with wrong password" in {
      this ?? (usernameUuid, BasicAccountSignUp(username, wrongPassword, None)) await {
        case r: InvalidPassword =>
          assert(r.errors.contains("UPPER_CASE_CHARACTER"))
          assert(r.errors.contains("NUMBER_CHARACTER"))
          succeed
        case _ => fail()
      }
    }

    "fail if confirmed password does not match password" in {
      this ?? (usernameUuid, BasicAccountSignUp(username, password, Some("fake"))) await {
        case PasswordsNotMatched => succeed
        case _                   => fail()
      }
    }

    "work with username" in {
      this ?? (usernameUuid, BasicAccountSignUp(username, password)) await {
        case r: AccountCreated =>
          import r._
          account.status shouldBe AccountStatus.Active
          account.email.isDefined shouldBe false
          account.gsm.isDefined shouldBe false
          account.username.isDefined shouldBe true
        case other => fail(other.getClass)
      }
    }

    "fail if account already exists" in {
      this ?? (usernameUuid, BasicAccountSignUp(username, password)) await {
        case AccountAlreadyExists => succeed
        case _                    => fail()
      }
    }

    "fail if username already exists" in {
      this ?? (usernameUuid2, BasicAccountSignUp(username, password)) await {
        case LoginAlreadyExists => succeed
        case _                  => fail()
      }
    }

    "work with email" in {
      this ?? (emailUuid, BasicAccountSignUp(email, password)) await {
        case r: AccountCreated =>
          import r._
          account.status shouldBe AccountStatus.Inactive
          account.email.isDefined shouldBe true
          account.gsm.isDefined shouldBe false
          account.username.isDefined shouldBe false
        case other => fail(other.toString)
      }
    }

    "fail if email already exists" in {
      this ?? (emailUuid2, BasicAccountSignUp(email, password)) await {
        case LoginAlreadyExists => succeed
        case _                  => fail()
      }
    }

    "work with gsm" in {
      this ?? (gsmUuid, BasicAccountSignUp(gsm, password)) await {
        case r: AccountCreated =>
          import r._
          account.status shouldBe AccountStatus.Active
          account.email.isDefined shouldBe false
          account.gsm.isDefined shouldBe true
          account.username.isDefined shouldBe false
        case _ => fail()
      }
    }

    "fail if gsm already exists" in {
      this ?? (gsmUuid2, BasicAccountSignUp(gsm, password)) await {
        case LoginAlreadyExists => succeed
        case _                  => fail()
      }
    }

    "work with anonymous account" in {
      this ?? (anonymousUuid, BasicAccountSignUp(anonymous, password)) await {
        case r: AccountCreated =>
          import r._
          account.anonymous.getOrElse(true) shouldBe false
          account.status shouldBe AccountStatus.Active
          account.email.isDefined shouldBe false
          account.gsm.isDefined shouldBe false
          account.username.isDefined shouldBe true
          account.username.get shouldBe anonymous
        case other => fail(other.getClass)
      }
    }

  }

  "Activation" should {
    "fail if account is not found" in {
      this ?? (generateUUID(Some("fake")), Activate("fake")) await {
        case AccountNotFound => succeed
        case _               => fail()
      }
    }

    "fail if account is not inactive" in {
      this ? (usernameUuid, Activate("fake")) await {
        case IllegalStateError => succeed
        case _                 => fail()
      }
    }

    "fail if token does not await activation token" in {
      this ? (emailUuid, Activate("fake")) await {
        case InvalidToken => succeed
        case other        => fail(other.toString)
      }
    }

    "work if account is inactive and token matches activation token" in {
      val token = generateToken(emailUuid).token
      this ?? (token, Activate(token)) await {
        case e: AccountActivated =>
          import e.account._
          verificationToken.isEmpty shouldBe true
          status shouldBe AccountStatus.Active
          uuid shouldBe emailUuid
        case other => fail(other.toString)
      }
    }
  }

  "Login" should {
    "work with matching username and password" in {
      this ?? (username, Login(username, password)) await {
        case _: LoginSucceededResult => succeed
        case _                       => fail()
      }
    }
    "fail with unknown username" in {
      this ?? ("fake", Login("fake", password)) await {
        case LoginAndPasswordNotMatched => succeed
        case other                      => fail(other.toString)
      }
    }
    "fail with unmatching username and password" in {
      this ?? (username, Login(username, "fake")) await {
        case LoginAndPasswordNotMatched => succeed
        case _                          => fail()
      }
    }
    "work with matching email and password" in {
      this ?? (email, Login(email, password)) await {
        case _: LoginSucceededResult => succeed
        case _                       => fail()
      }
    }
    "fail with unknown email" in {
      this ?? (computeEmail("fake"), Login(computeEmail("fake"), password)) await {
        case LoginAndPasswordNotMatched => succeed
        case other                      => fail(other.toString)
      }
    }
    "fail with unmatching email and password" in {
      this ?? (email, Login(email, "fake")) await {
        case LoginAndPasswordNotMatched => succeed
        case _                          => fail()
      }
    }
    "work with matching gsm and password" in {
      this ?? (gsm, Login(gsm, password)) await {
        case _: LoginSucceededResult => succeed
        case _                       => fail()
      }
    }
    "fail with unknown gsm" in {
      this ?? ("0123456789", Login("0123456789", password)) await {
        case LoginAndPasswordNotMatched => succeed
        case other                      => fail(other.toString)
      }
    }
    "fail with unmatching gsm and password" in {
      this ?? (gsm, Login(gsm, "fake")) await {
        case LoginAndPasswordNotMatched => succeed
        case _                          => fail()
      }
    }
    "disable account after n login failures" in {
      this ?? (gsm, Login(gsm, password)) await {
        case _: LoginSucceededResult => succeed
        case _                       => fail()
      }
      (0 until AccountSettings.MaxLoginFailures) // max number of failures
        .map(_ =>
          this ?? (gsm, Login(gsm, "fake")) await {
            case LoginAndPasswordNotMatched => succeed
            case other                      => fail(other.getClass.toString)
          }
        )
      this ?? (gsm, Login(gsm, "fake")) await {
        case AccountDisabled => succeed
        case other           => fail(other.getClass.toString)
      }
    }
  }

  "Device" should {
    "be registered" in {
      devices.map(device =>
        this ?? (gsmUuid, RegisterDevice(
          gsmUuid,
          DeviceRegistration(device.regId, device.platform)
        )) await {
          case DeviceRegistered => succeed
          case other            => fail(other.getClass.toString)
        }
      )
    }
    "be unregistered" in {
      this ?? (gsmUuid, UnregisterDevice(gsmUuid, androidDevice.regId)) await {
        case DeviceUnregistered => succeed
        case other              => fail(other.getClass.toString)
      }
    }
  }

  "SendVerificationCode" should {
    "work with gsm" in {
      this ?? (gsm, SendVerificationCode(gsm)) await {
        case VerificationCodeSent => succeed
        case _                    => fail()
      }
    }
    "work with email" in {
      this ?? (email, SendVerificationCode(email)) await {
        case VerificationCodeSent => succeed
        case _                    => fail()
      }
    }
    "fail with username" in {
      this ?? (username, SendVerificationCode(username)) await {
        case InvalidPrincipal => succeed
        case _                => fail()
      }
    }
  }

  "SendResetPasswordToken" should {
    "work with gsm" in {
      this ?? (gsm, SendResetPasswordToken(gsm)) await {
        case ResetPasswordTokenSent => succeed
        case _                      => fail()
      }
    }
    "work with email" in {
      this ?? (email, SendResetPasswordToken(email)) await {
        case ResetPasswordTokenSent => succeed
        case _                      => fail()
      }
    }
    "fail with username" in {
      this ?? (username, SendResetPasswordToken(username)) await {
        case InvalidPrincipal => succeed
        case _                => fail()
      }
    }
  }

  "CheckResetPasswordToken" should {
    "work when a valid token has been generated for the corresponding account" in {
      val token = generateToken(emailUuid).token
      this ?? (token, CheckResetPasswordToken(token)) await {
        case ResetPasswordTokenChecked => succeed
        case _                         => fail()
      }
    }
    "fail when the token does not exist" in {
      this ?? ("fake", CheckResetPasswordToken("fake")) await {
        case TokenNotFound => succeed
        case _             => fail()
      }
    }
  }

  "ResetPassword" should {
    "work" in {
      val token = generateToken(emailUuid).token
      this ?? (token, ResetPassword(token, newPassword)) await {
        case _: PasswordReseted => succeed
        case _                  => fail()
      }
    }
  }

  "UpdatePassword" should {
    "work" in {
      this ?? (usernameUuid, UpdatePassword(username, password, newPassword)) await {
        case _: PasswordUpdated => succeed
        case _                  => fail()
      }
    }
  }

  "Unsubscribe" should {
    "work" in {
      this ?? (generateUUID(Some(computeEmail("Unsubscribe"))), BasicAccountSignUp(
        computeEmail("Unsubscribe"),
        password
      )) await {
        case r: AccountCreated =>
          r.account.status shouldBe AccountStatus.Inactive
        case _ => fail()
      }
      this ?? (generateUUID(Some(computeEmail("Unsubscribe"))), Unsubscribe(
        generateUUID(Some(computeEmail("Unsubscribe")))
      )) await {
        case _: AccountDeleted => succeed
        case _                 => fail()
      }
    }
  }

  "Destroy" should {
    "work" in {
      this ?? (generateUUID(Some(computeEmail("Unsubscribe"))), DestroyAccount) await {
        case r: AccountDestroyed =>
        case _                   => fail()
      }
    }
  }

  "Logout" should {
    "work" in {
      this ?? (usernameUuid, Logout) await {
        case LogoutSucceeded => succeed
        case _               => fail()
      }
    }
  }

  "UpdateProfile" should {
    "work" in {
      val uuid = usernameUuid
      this ?? (uuid,
      UpdateProfile(
        uuid,
        BasicAccountProfile.defaultInstance
          .withUuid(uuid)
          .withName("test")
          .copyWithDetails(
            Some(
              BasicAccountDetails.defaultInstance
                .withUuid(uuid)
                .withFirstName(firstname)
                .withPhoneNumber(gsm2)
            )
          )
      )) await {
        case ProfileUpdated => succeed
        case _              => fail()
      }
    }
  }

  "SwitchProfile" should {
    "work" in {
      this ?? (usernameUuid, SwitchProfile(usernameUuid, "test")) await {
        case r: ProfileSwitched =>
          r.profile match {
            case Some(s2) =>
              s2.firstName shouldBe firstname
              s2.phoneNumber shouldBe Some(gsm2)
            case _ => fail()
          }
        case _ => fail()
      }
    }
  }

  "LoadProfile" should {
    "work" in {
      this ?? (usernameUuid, LoadProfile(usernameUuid, Some("test"))) await {
        case r: ProfileLoaded =>
          import r._
          profile.firstName shouldBe firstname
          profile.phoneNumber shouldBe Some(gsm2)
        case _ => fail()
      }
    }
  }

  "UpdateProfile with Principal" should {
    "work" in {
      val uuid = emailUuid
      this ?? (uuid,
      UpdateProfile(
        uuid,
        BasicAccountProfile.defaultInstance
          .withUuid(uuid)
          .withName("test")
          .copyWithDetails(
            Some(
              BasicAccountDetails.defaultInstance
                .withUuid(uuid)
                .withFirstName(firstname)
                .withEmail(email2)
            )
          )
      )) await {
        case ProfileUpdated =>
          this ?? (email2, Login(email2, newPassword)) await {
            case r: LoginSucceededResult =>
              import r._
              account.details.map(_.firstName).getOrElse("") shouldBe firstname
              account.email.getOrElse("") shouldBe email2
            case other => fail(other.toString)
          }
        case _ => fail()
      }
    }
  }

  "UpdateLogin" should {
    "work" in {
      val oldLogin = email2
      val newLogin = email
      this ?? (emailUuid,
      UpdateLogin(
        oldLogin,
        newLogin,
        newPassword
      )) await {
        case LoginUpdated =>
          this ?? (oldLogin, Login(oldLogin, newPassword)) await {
            case LoginAndPasswordNotMatched =>
              this ?? (newLogin, Login(newLogin, newPassword)) await {
                case r: LoginSucceededResult =>
                  import r._
                  account.email.getOrElse("") shouldBe newLogin
                  // #MOSA-454
                  this ?? (emailUuid2, BasicAccountSignUp(oldLogin, password)) await {
                    case r: AccountCreated =>
                      import r._
                      account.status shouldBe AccountStatus.Inactive
                      account.email.isDefined shouldBe true
                      account.gsm.isDefined shouldBe false
                      account.username.isDefined shouldBe false
                      this ?? (newLogin, Login(newLogin, newPassword)) await {
                        case _: LoginSucceededResult => succeed
                        case other                   => fail(other.toString)
                      }
                    case other => fail(other.toString)
                  }
                case other => fail(other.toString)
              }
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }
  }
}
