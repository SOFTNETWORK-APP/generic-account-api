package app.softnetwork.account.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.handlers.MockGenerator._
import app.softnetwork.account.message._
import app.softnetwork.account.model._
import app.softnetwork.account.service.AccountService
import app.softnetwork.notification.model.Platform
import app.softnetwork.persistence._
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import org.scalatest.wordspec.AnyWordSpecLike

/** Created by smanciot on 18/04/2020.
  */
trait AccountServiceSpec[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  PV <: ProfileView,
  DV <: AccountDetailsView,
  AV <: AccountView[PV, DV],
  SD <: SessionData with SessionDataDecorator[SD]
] extends AccountService[PV, DV, AV, SD]
    with AnyWordSpecLike
    with AccountTestKit[T, P] {
  _: CommandTypeKey[AccountCommand] with ManifestWrapper[AV] with SessionMaterials[SD] =>

  implicit lazy val system: ActorSystem[_] = typedSystem()

  private val username = "test"

  private val firstname = "Stéphane"

  private def computeEmail(user: String) = s"$user@gmail.com"

  private val email = computeEmail(username)

  private val gsm = "33660010203"

  private val gsm2 = "33660020304"

  private val regId = "regId"

  private val pwd = "Changeit1"

  private val newPassword = "Changeit2"

  private val usernameUuid: String = generateUUID(Some(username))

  private val emailUuid: String = generateUUID(Some(email))

  private val gsmUuid: String = generateUUID(Some(gsm))

  "SignUp" should {
    "fail if confirmed password does not match password" in {
      run("PasswordsNotMatched", BasicAccountSignUp(username, pwd, Some("fake"))) await {
        case PasswordsNotMatched => succeed
        case _                   => fail()
      }
    }

    "work with username" in {
      run(usernameUuid, BasicAccountSignUp(username, pwd)) await {
        case r: AccountCreated => r.account.status shouldBe AccountStatus.Active
        case _                 => fail()
      }
    }

    "fail if username already exists" in {
      run(usernameUuid, BasicAccountSignUp(username, pwd)) await {
        case AccountAlreadyExists => succeed
        case other                => fail(other.toString)
      }
    }

    "work with email" in {
      run(emailUuid, BasicAccountSignUp(email, pwd)) await {
        case r: AccountCreated => r.account.status shouldBe AccountStatus.Inactive
        case _                 => fail()
      }
    }

    "fail if email already exists" in {
      run(emailUuid, BasicAccountSignUp(email, pwd)) await {
        case AccountAlreadyExists => succeed
        case _                    => fail()
      }
    }

    "work with gsm" in {
      run(gsmUuid, BasicAccountSignUp(gsm, pwd)) await {
        case r: AccountCreated => r.account.status shouldBe AccountStatus.Active
        case _                 => fail()
      }
    }

    "fail if gsm already exists" in {
      run(gsmUuid, BasicAccountSignUp(gsm, pwd)) await {
        case AccountAlreadyExists => succeed
        case _                    => fail()
      }
    }

  }

  "Activation" should {
    "fail if account is not found" in {
      ??(generateUUID(Some("fake")), Activate("fake")) await {
        case AccountNotFound => succeed
        case _               => fail()
      }
    }

    "fail if account is not inactive" in {
      ?(usernameUuid, Activate("fake")) await {
        case IllegalStateError => succeed
        case other             => fail(other.toString)
      }
    }

    "fail if token does not match activation token" in {
      ?(emailUuid, Activate("fake")) await {
        case InvalidToken => succeed
        case other        => fail(other.toString)
      }
    }

    "work if account is inactive and token matches activation token" in {
      val token = computeToken(emailUuid)
      run(token, Activate(token)) await {
        case e: AccountActivated =>
          import e.account._
          verificationToken.isEmpty shouldBe true
          status shouldBe AccountStatus.Active
          uuid shouldBe emailUuid
        case _ => fail()
      }
    }
  }

  "Login" should {
    "work with matching username and password" in {
      run(username, Login(username, pwd)) await {
        case _: LoginSucceededResult => succeed
        case _                       => fail()
      }
    }
    "fail with unknown username" in {
      run("fake", Login("fake", pwd)) await {
        case LoginAndPasswordNotMatched => succeed
        case _                          => fail()
      }
    }
    "fail with unmatching username and password" in {
      run(username, Login(username, "fake")) await {
        case LoginAndPasswordNotMatched => succeed
        case _                          => fail()
      }
    }
    "work with matching email and password" in {
      run(email, Login(email, pwd)) await {
        case _: LoginSucceededResult => succeed
        case _                       => fail()
      }
    }
    "fail with unknown email" in {
      run(computeEmail("fake"), Login(computeEmail("fake"), pwd)) await {
        case LoginAndPasswordNotMatched => succeed
        case _                          => fail()
      }
    }
    "fail with unmatching email and password" in {
      run(email, Login(email, "fake")) await {
        case LoginAndPasswordNotMatched => succeed
        case _                          => fail()
      }
    }
    "work with matching gsm and password" in {
      run(gsm, Login(gsm, pwd)) await {
        case _: LoginSucceededResult => succeed
        case _                       => fail()
      }
    }
    "fail with unknown gsm" in {
      run(generateUUID(Some("0123456789")), Login("0123456789", pwd)) await {
        case LoginAndPasswordNotMatched => succeed
        case _                          => fail()
      }
    }
    "fail with unmatching gsm and password" in {
      run(gsm, Login(gsm, "fake")) await {
        case LoginAndPasswordNotMatched => succeed
        case _                          => fail()
      }
    }
    "disable account after n login failures" in {
      this ?? (gsm, Login(gsm, pwd)) await {
        case _: LoginSucceededResult => succeed
        case _                       => fail()
      }
      (0 until AccountSettings.MaxLoginFailures) // max number of failures
        .map(_ =>
          run(gsm, Login(gsm, "fake")) await {
            case LoginAndPasswordNotMatched => succeed
            case other                      => fail(other.getClass.toString)
          }
        )
      run(gsm, Login(gsm, "fake")) await {
        case AccountDisabled => succeed
        case other           => fail(other.getClass.toString)
      }
    }
  }

  "UpdateProfile" should {
    "work" in {
      val uuid = usernameUuid
      val profile =
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
      run(uuid, UpdateProfile(uuid, profile)) await {
        case ProfileUpdated => succeed
        case _              => fail()
      }
    }
  }

  "SwitchProfile" should {
    "work" in {
      val uuid = usernameUuid
      run(uuid, SwitchProfile(uuid, "test")) await {
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
      val uuid = usernameUuid
      run(uuid, LoadProfile(uuid, Some("test"))) await {
        case r: ProfileLoaded =>
          import r._
          profile.firstName shouldBe firstname
          profile.phoneNumber shouldBe Some(gsm2)
        case _ => fail()
      }
    }
  }

  "SendVerificationCode" should {
    "work with gsm" in {
      run(gsm, SendVerificationCode(gsm)) await {
        case VerificationCodeSent => succeed
        case other                => fail(other.toString)
      }
    }
    "work with email" in {
      run(email, SendVerificationCode(email)) await {
        case VerificationCodeSent => succeed
        case _                    => fail()
      }
    }
    "fail with username" in {
      run(username, SendVerificationCode(username)) await {
        case InvalidPrincipal => succeed
        case _                => fail()
      }
    }
  }

  "SendResetPasswordToken" should {
    "work with gsm" in {
      run(gsm, SendResetPasswordToken(gsm)) await {
        case ResetPasswordTokenSent => succeed
        case _                      => fail()
      }
    }
    "work with email" in {
      run(email, SendResetPasswordToken(email)) await {
        case ResetPasswordTokenSent => succeed
        case _                      => fail()
      }
    }
    "fail with username" in {
      run(username, SendResetPasswordToken(username)) await {
        case InvalidPrincipal => succeed
        case _                => fail()
      }
    }
  }

  "CheckResetPasswordToken" should {
    "work when a valid token has been generated for the corresponding account" in {
      val token = computeToken(emailUuid)
      run(token, CheckResetPasswordToken(token)) await {
        case ResetPasswordTokenChecked => succeed
        case _                         => fail()
      }
    }
    "fail when the token does not exist" in {
      run("fake", CheckResetPasswordToken("fake")) await {
        case TokenNotFound => succeed
        case _             => fail()
      }
    }
  }

  "ResetPassword" should {
    "work" in {
      val token = computeToken(emailUuid)
      run(token, ResetPassword(token, newPassword)) await {
        case _: PasswordReseted => succeed
        case _                  => fail()
      }
    }
  }

  "UpdatePassword" should {
    "work" in {
      run(usernameUuid, UpdatePassword(username, pwd, newPassword)) await {
        case _: PasswordUpdated => succeed
        case _                  => fail()
      }
    }
  }

  "Device" should {
    "be registered" in {
      run(
        generateUUID(Some(computeEmail("DeviceRegistration"))),
        BasicAccountSignUp(computeEmail("DeviceRegistration"), pwd)
      ) await {
        case r: AccountCreated =>
          r.account.status shouldBe AccountStatus.Inactive
          run(
            generateUUID(Some(computeEmail("DeviceRegistration"))),
            RegisterDevice(
              generateUUID(Some(computeEmail("DeviceRegistration"))),
              DeviceRegistration(regId, Platform.IOS)
            )
          ) await {
            case DeviceRegistered => succeed
            case _                => fail()
          }
        case _ => fail()
      }
    }

    "be unregistered" in {
      run(
        generateUUID(Some(computeEmail("DeviceRegistration"))),
        UnregisterDevice(generateUUID(Some(computeEmail("DeviceRegistration"))), regId)
      ) await {
        case DeviceUnregistered => succeed
        case _                  => fail()
      }
    }
  }

  "Unsubscribe" should {
    "work" in {
      run(
        generateUUID(Some(computeEmail("DeviceRegistration"))),
        Unsubscribe("DeviceRegistration")
      ) await {
        case _: AccountDeleted => succeed
        case _                 => fail()
      }
    }
  }

  "Logout" should {
    "work" in {
      run(usernameUuid, Logout) await {
        case LogoutSucceeded => succeed
        case _               => fail()
      }
    }
  }

}
