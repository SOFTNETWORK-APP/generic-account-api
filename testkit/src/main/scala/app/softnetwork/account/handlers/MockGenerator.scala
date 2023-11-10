package app.softnetwork.account.handlers

import app.softnetwork.account.model.{ExpirationDate, VerificationCode, VerificationToken}

object MockGenerator {
  val token = "token"
  val code = "code"

  def computeToken(uuid: String) = s"$uuid-$token"
}

trait MockGenerator extends DefaultGenerator with ExpirationDate {

  import MockGenerator._

  override def generateToken(uuid: String, expiryTimeInMinutes: Int): VerificationToken = {
    VerificationToken(computeToken(uuid), compute(expiryTimeInMinutes))
  }

  override def generatePinCode(pinSize: Int, expiryTimeInMinutes: Int): VerificationCode = {
    VerificationCode(code, compute(expiryTimeInMinutes))
  }

}
