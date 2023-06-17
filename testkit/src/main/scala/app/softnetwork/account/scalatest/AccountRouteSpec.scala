package app.softnetwork.account.scalatest

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.handlers.{AccountKeyDao, MockGenerator}
import app.softnetwork.account.message._
import app.softnetwork.account.model.{
  Account,
  AccountDecorator,
  AccountStatus,
  AccountView,
  Profile,
  ProfileDecorator
}
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.api.server.config.ServerSettings._
import app.softnetwork.serialization._
import org.scalatest.wordspec.AnyWordSpecLike

/** Created by smanciot on 22/03/2018.
  */
trait AccountRouteSpec[T <: Account with AccountDecorator, P <: Profile with ProfileDecorator]
    extends AnyWordSpecLike
    with AccountRouteTestKit[T, P] { _: ApiRoutes =>

  private val anonymous = "anonymous"

  private val username = "smanciot"

  private val firstName = Some("Stephane")

  private val lastName = Some("Manciot")

  private val email = "stephane.manciot@gmail.com"

  private val gsm = "33660010203"

  private val password = "Changeit1"

  def profile: P

  "MainRoutes" should {
    "contain a healthcheck path" in {
      Get(s"/$RootPath/healthcheck") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "Anonymous SignUp" should {
    "work" in {
      Post(s"/$RootPath/${AccountSettings.Path}/anonymous") ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        val account = responseAs[AccountView]
        account.status shouldBe AccountStatus.Active
        account.anonymous.getOrElse(false) shouldBe true
        httpHeaders = extractHeaders(headers)
      }
    }
  }

  "SignUp" should {
    "work with anonymous account" in {
      withHeaders(
        Post(
          s"/$RootPath/${AccountSettings.Path}/signUp",
          implicitly[SignUp]((anonymous, password, Some(profile)))
        )
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        val account = responseAs[AccountView]
        account.status shouldBe AccountStatus.Active
        account.anonymous.getOrElse(true) shouldBe false
        account.fromAnonymous.getOrElse(false) shouldBe true
      }
    }
    "fail if confirmed password does not match password" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/signUp",
        BasicAccountSignUp(username, password, Some("fake"))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldBe PasswordsNotMatched.message
      }
    }
    "work with username" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/signUp",
        implicitly[SignUp]((username, password, Some(profile)))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
    "fail if username already exists" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/signUp",
        BasicAccountSignUp(username, password)
      ) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldBe LoginAlreadyExists.message
      }
    }
    "work with email" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/signUp",
        implicitly[SignUp]((email, password, Some(profile)))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        val account = responseAs[AccountView]
        if (AccountSettings.ActivationEnabled) {
          account.status shouldBe AccountStatus.Inactive
        } else {
          account.status shouldBe AccountStatus.Active
        }
      }
    }
    "fail if email already exists" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/signUp",
        BasicAccountSignUp(email, password)
      ) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAlreadyExists.message
      }
    }
    "work with gsm" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/signUp",
        implicitly[SignUp]((gsm, password, Some(profile)))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
    "fail if gsm already exists" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/signUp",
        BasicAccountSignUp(gsm, password)
      ) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAlreadyExists.message
      }
    }
  }

  "basic" should {
    "work with matching username and password" in {
      val validCredentials = BasicHttpCredentials(username, password)
      Post(s"/$RootPath/${AccountSettings.Path}/basic") ~> addCredentials(
        validCredentials
      ) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
  }

  "Login" should {
    "work with matching username and password within an anonymous session" in {
      withHeaders(
        Post(s"/$RootPath/${AccountSettings.Path}/login", Login(username, password))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
    "work with matching email and password" in {
      AccountKeyDao.lookupAccount(email)(typedSystem()) await {
        case Some(uuid) =>
          Get(
            s"/$RootPath/${AccountSettings.Path}/activate",
            Activate(MockGenerator.computeToken(uuid))
          ) ~> routes
          Post(s"/$RootPath/${AccountSettings.Path}/login", Login(email, password)) ~> mainRoutes(
            typedSystem()
          ) ~> check {
            status shouldEqual StatusCodes.OK
            responseAs[AccountView].status shouldBe AccountStatus.Active
          }
        case _ => fail()
      }
    }
    "work with matching gsm and password" in {
      Post(s"/$RootPath/${AccountSettings.Path}/login", Login(gsm, password)) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
    "fail with unknown username" in {
      Post(s"/$RootPath/${AccountSettings.Path}/login", Login("fake", password)) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unknown email" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/login",
        Login("fake@gmail.com", password)
      ) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unknown gsm" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/login",
        Login("0102030405", password)
      ) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching username and password" in {
      Post(s"/$RootPath/${AccountSettings.Path}/login", Login(username, "fake")) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching email and password" in {
      Post(s"/$RootPath/${AccountSettings.Path}/login", Login(email, "fake")) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching gsm and password" in {
      Post(s"/$RootPath/${AccountSettings.Path}/login", Login(gsm, "fake")) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "disable account after n login failures" in {
      Post(s"/$RootPath/${AccountSettings.Path}/login", Login(gsm, password)) ~> mainRoutes(
        typedSystem()
      ) // reset number of failures
      (0 until AccountSettings.MaxLoginFailures) // max number of failures
        .map(_ =>
          Post(s"/$RootPath/${AccountSettings.Path}/login", Login(gsm, "fake")) ~> mainRoutes(
            typedSystem()
          )
        )
      Post(s"/$RootPath/${AccountSettings.Path}/login", Login(gsm, "fake")) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual AccountDisabled.message
      }
    }
  }

  "ResetPassword" should {
    "work" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/verificationCode",
        SendVerificationCode(gsm)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        httpHeaders = extractHeaders(headers)
      }
      withHeaders(
        Post(
          s"/$RootPath/${AccountSettings.Path}/resetPassword",
          ResetPassword(MockGenerator.code, password)
        )
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "Logout" should {
    "work" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/login",
        Login(gsm, password, refreshable = true)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        httpHeaders = extractHeaders(headers)
      }
      withHeaders(Post(s"/$RootPath/${AccountSettings.Path}/logout")) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "Unsubscribe" should {
    "work" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/login",
        Login(gsm, password, refreshable = true)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        httpHeaders = extractHeaders(headers)
      }
      withHeaders(Post(s"/$RootPath/${AccountSettings.Path}/unsubscribe")) ~> mainRoutes(
        typedSystem()
      ) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccountView].status shouldEqual AccountStatus.Deleted
      }
    }
  }

  "SendVerificationCode" should {
    "work with email" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/verificationCode",
        SendVerificationCode(email)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "work with gsm" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/verificationCode",
        SendVerificationCode(gsm)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

}
