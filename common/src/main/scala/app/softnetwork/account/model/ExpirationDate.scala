package app.softnetwork.account.model

import app.softnetwork.account.config.AccountSettings.{
  AccessTokenExpirationTime,
  AuthorizationCodeExpirationTime
}

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
      compute(AuthorizationCodeExpirationTime)
    )
  }
}

trait AccessTokenCompanion extends ExpirationToken {

  def apply(prefix: String, scope: Option[String]): AccessToken = {
    AccessToken.defaultInstance
      .withToken(generateToken(prefix))
      .withExpirationDate(compute(AccessTokenExpirationTime))
      .withRefreshToken(generateToken(prefix))
      .copy(scope = scope)
  }

}
