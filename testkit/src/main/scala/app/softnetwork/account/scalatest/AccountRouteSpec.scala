package app.softnetwork.account.scalatest

import akka.http.scaladsl.model.{FormData, HttpProtocols, StatusCodes}
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.handlers.{AccountKeyDao, MockGenerator}
import app.softnetwork.account.message._
import app.softnetwork.account.model.{
  Account,
  AccountDecorator,
  AccountDetailsView,
  AccountStatus,
  AccountView,
  AuthorizationCode,
  Profile,
  ProfileDecorator,
  ProfileView
}
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.api.server.config.ServerSettings._
import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.serialization._
import org.scalatest.wordspec.AnyWordSpecLike

/** Created by smanciot on 22/03/2018.
  */
trait AccountRouteSpec[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  PV <: ProfileView,
  DV <: AccountDetailsView,
  AV <: AccountView[PV, DV]
] extends AnyWordSpecLike
    with AccountRouteTestKit[T, P] { _: ApiRoutes with ManifestWrapper[AV] =>

  protected val anonymous = "anonymous"

  protected val username = "smanciot"

  protected val firstName = "Pascal"

  protected val lastName = "Dupont"

  protected val email = "pascal.dupont@gmail.com"

  protected val gsm = "33660010203"

  protected val password = "Changeit1"

  protected var authorizationCode: String = _

  protected var accessToken: String = _

  protected var refreshToken: String = _

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
      implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
      Post(s"/$RootPath/${AccountSettings.Path}/anonymous") ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        val account = responseAs[AV]
        account.status shouldBe AccountStatus.Active
        account.anonymous.getOrElse(false) shouldBe true
        httpHeaders = extractHeaders(headers)
      }
    }
  }

  "SignUp" should {
    "work with anonymous account" in {
      implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
      withHeaders(
        Post(
          s"/$RootPath/${AccountSettings.Path}/signUp",
          implicitly[SignUp]((anonymous, password, Some(profile)))
        )
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        val account = responseAs[AV]
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
      implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
      Post(
        s"/$RootPath/${AccountSettings.Path}/signUp",
        implicitly[SignUp]((username, password, Some(profile)))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[AV].status shouldBe AccountStatus.Active
      }
    }
    "fail if username already exists" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/signUp",
        BasicAccountSignUp(username, password)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldBe LoginAlreadyExists.message
      }
    }
    "work with email" in {
      implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
      Post(
        s"/$RootPath/${AccountSettings.Path}/signUp",
        implicitly[SignUp]((email, password, Some(profile)))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        val account = responseAs[AV]
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
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAlreadyExists.message
      }
    }
    "work with gsm" in {
      implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
      Post(
        s"/$RootPath/${AccountSettings.Path}/signUp",
        implicitly[SignUp]((gsm, password, Some(profile)))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[AV].status shouldBe AccountStatus.Active
      }
    }
    "fail if gsm already exists" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/signUp",
        BasicAccountSignUp(gsm, password)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAlreadyExists.message
      }
    }
  }

  "basic" should {
    "work with matching username and password" in {
      implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
      val validCredentials = BasicHttpCredentials(username, password)
      Post(s"/$RootPath/${AccountSettings.Path}/basic") ~> addCredentials(
        validCredentials
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AV].status shouldBe AccountStatus.Active
      }
    }
  }

  "oauth" should {
    "should generate authorization code with matching username and password" in {
      val validCredentials = BasicHttpCredentials(username, password)
      Get(
        s"/$RootPath/${AccountSettings.OAuthPath}/authorize?response_type=code&client_id=test"
      ) ~> addCredentials(
        validCredentials
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        authorizationCode = responseAs[AuthorizationCode].code
        authorizationCode should not be empty
      }
    }
    "should generate access token with matching authorization code" in {
      Post(
        s"/$RootPath/${AccountSettings.OAuthPath}/token",
        FormData(
          "grant_type"   -> "authorization_code",
          "code"         -> authorizationCode,
          "client_id"    -> "test",
          "redirect_uri" -> "" // http://localhost:8080"
        ).toEntity
      ).withProtocol(HttpProtocols.`HTTP/1.1`) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val tokens = responseAs[Tokens]
        accessToken = tokens.access_token
        accessToken should not be empty
        refreshToken = tokens.refresh_token
        refreshToken should not be empty
      }
    }
    "should refresh access token" in {
      Post(
        s"/$RootPath/${AccountSettings.OAuthPath}/token",
        FormData(
          "grant_type"    -> "refresh_token",
          "refresh_token" -> refreshToken
        ).toEntity
      ).withProtocol(HttpProtocols.`HTTP/1.1`) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val tokens = responseAs[Tokens]
        val accessToken2 = tokens.access_token
        accessToken2 should not be empty
        assert(accessToken != accessToken2)
        accessToken = accessToken2
        val refreshToken2 = tokens.refresh_token
        refreshToken2 should not be empty
        assert(refreshToken != refreshToken2)
        refreshToken = refreshToken2
      }
    }
    "should retrieve me with matching bearer access token" in {
      implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
      Get(
        s"/$RootPath/${AccountSettings.OAuthPath}/me"
      ) ~> addCredentials(
        OAuth2BearerToken(accessToken)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val me = responseAs[Me]
        me.firstName shouldBe firstName
        me.lastName shouldBe lastName
      }
    }
  }

  "Login" should {
    "work with matching username and password within an anonymous session" in {
      implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
      withHeaders(
        Post(s"/$RootPath/${AccountSettings.Path}/login", Login(username, password))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AV].status shouldBe AccountStatus.Active
      }
    }
    "work with matching email and password" in {
      implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
      AccountKeyDao.lookupAccount(email)(typedSystem()) await {
        case Some(uuid) =>
          Get(
            s"/$RootPath/${AccountSettings.Path}/activate",
            Activate(MockGenerator.computeToken(uuid))
          ) ~> routes
          Post(
            s"/$RootPath/${AccountSettings.Path}/login",
            Login(email, password)
          ) ~> routes ~> check {
            status shouldEqual StatusCodes.OK
            responseAs[AV].status shouldBe AccountStatus.Active
          }
        case _ => fail()
      }
    }
    "work with matching gsm and password" in {
      implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
      Post(s"/$RootPath/${AccountSettings.Path}/login", Login(gsm, password)) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AV].status shouldBe AccountStatus.Active
      }
    }
    "fail with unknown username" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/login",
        Login("fake", password)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unknown email" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/login",
        Login("fake@gmail.com", password)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unknown gsm" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/login",
        Login("0102030405", password)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching username and password" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/login",
        Login(username, "fake")
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching email and password" in {
      Post(s"/$RootPath/${AccountSettings.Path}/login", Login(email, "fake")) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching gsm and password" in {
      Post(s"/$RootPath/${AccountSettings.Path}/login", Login(gsm, "fake")) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "disable account after n login failures" in {
      Post(
        s"/$RootPath/${AccountSettings.Path}/login",
        Login(gsm, password)
      ) ~> routes // reset number of failures
      (0 until AccountSettings.MaxLoginFailures) // max number of failures
        .map(_ => Post(s"/$RootPath/${AccountSettings.Path}/login", Login(gsm, "fake")) ~> routes)
      Post(s"/$RootPath/${AccountSettings.Path}/login", Login(gsm, "fake")) ~> routes ~> check {
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
      withHeaders(Post(s"/$RootPath/${AccountSettings.Path}/logout")) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        httpHeaders = extractHeaders(headers)
      }
    }
  }

  "Unsubscribe" should {
    "work" in {
      implicit val manifest: Manifest[AV] = manifestWrapper.wrapped
      Post(
        s"/$RootPath/${AccountSettings.Path}/login",
        Login(gsm, password, refreshable = true)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        httpHeaders = extractHeaders(headers)
      }
      withHeaders(Post(s"/$RootPath/${AccountSettings.Path}/unsubscribe")) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AV].status shouldEqual AccountStatus.Deleted
        httpHeaders = extractHeaders(headers)
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
