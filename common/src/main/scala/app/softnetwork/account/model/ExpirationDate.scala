package app.softnetwork.account.model

import app.softnetwork.account.config.AccountSettings.OAuthSettings

import java.security.SecureRandom
import app.softnetwork.specification.{Rule, Specification}

import scala.language.reflectiveCalls
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Created by smanciot on 14/04/2018.
  */
trait ExpirationDate {

  def compute(expiryTimeInMinutes: Int): Instant = {
    Instant.now().plus(expiryTimeInMinutes, ChronoUnit.MINUTES)
  }

}

trait ExpirationCode extends ExpirationDate {
  def generateCode(pinSize: Int): String =
    s"%0${pinSize}d".format(new SecureRandom().nextInt(math.pow(10, pinSize).toInt))
}

trait ExpirationToken extends ExpirationDate {
  def generateToken(prefix: String): String = BearerTokenGenerator.generateSHAToken(prefix)
}

trait VerificationExpirationDate {
  def expirationDate: Instant
  final def expired: Boolean = Specification(ExpirationDateRule).isSatisfiedBy(this)
  final def expiresIn: Int =
    Math.max(0, (expirationDate.getEpochSecond - Instant.now().getEpochSecond).toInt)
}

case object ExpirationDateRule extends Rule[VerificationExpirationDate] {
  override def isSatisfiedBy(a: VerificationExpirationDate): Boolean =
    a.expirationDate.isBefore(Instant.now())
}

trait VerificationTokenCompanion extends ExpirationToken {

  def apply(login: String, expiryTimeInMinutes: Int): VerificationToken = {
    VerificationToken(generateToken(login), compute(expiryTimeInMinutes))
  }

}

trait VerificationCodeCompanion extends ExpirationCode {

  def apply(pinSize: Int, expiryTimeInMinutes: Int): VerificationCode = {
    VerificationCode(
      generateCode(pinSize),
      compute(expiryTimeInMinutes)
    )
  }

}

trait AuthorizationCodeCompanion extends ExpirationToken {
  def apply(
    clientId: String,
    scope: Option[String],
    redirectUri: Option[String],
    state: Option[String]
  ): AuthorizationCode = {
    AuthorizationCode(
      generateToken(clientId),
      scope,
      redirectUri,
      state,
      compute(OAuthSettings.authorizationCode.expirationTime)
    )
  }
}

trait AccessTokenCompanion extends ExpirationToken {

  def apply(prefix: String, scope: Option[String]): AccessToken = {
    AccessToken.defaultInstance
      .withToken(generateToken(prefix))
      .withExpirationDate(compute(OAuthSettings.accessToken.expirationTime))
      .withRefreshToken(generateToken(prefix))
      .withRefreshExpirationDate(compute(OAuthSettings.refreshToken.expirationTime))
      .copy(scope = scope)
  }

}

trait AccessTokenDecorator extends VerificationExpirationDate { _: AccessToken =>
  private lazy val refreshVerification: Option[VerificationExpirationDate] =
    refreshExpirationDate.map(exp =>
      new VerificationExpirationDate {
        override def expirationDate: Instant = exp
      }
    )
  def refreshExpired: Boolean = refreshVerification.exists(_.expired)
  def refreshExpiresIn: Option[Int] = refreshVerification.map(_.expiresIn)
}
