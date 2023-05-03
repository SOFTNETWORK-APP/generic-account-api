package app.softnetwork.account.model

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

trait VerificationExpirationDate {
  def expirationDate: Instant
  final def expired: Boolean = Specification(ExpirationDateRule).isSatisfiedBy(this)
}

case object ExpirationDateRule extends Rule[VerificationExpirationDate] {
  override def isSatisfiedBy(a: VerificationExpirationDate): Boolean =
    a.expirationDate.isBefore(Instant.now())
}

trait VerificationTokenCompanion extends ExpirationDate {

  def apply(login: String, expiryTimeInMinutes: Int): VerificationToken = {
    VerificationToken(BearerTokenGenerator.generateSHAToken(login), compute(expiryTimeInMinutes))
  }

}

trait VerificationCodeCompanion extends ExpirationDate {

  def apply(pinSize: Int, expiryTimeInMinutes: Int): VerificationCode = {
    VerificationCode(
      s"%0${pinSize}d".format(new SecureRandom().nextInt(math.pow(10, pinSize).toInt)),
      compute(expiryTimeInMinutes)
    )
  }

}
