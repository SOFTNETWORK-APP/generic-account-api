package app.softnetwork.account.handlers

import app.softnetwork.account.model.{VerificationCode, VerificationToken}

/** Created by smanciot on 09/04/2018.
  */
trait Generator {
  val oneDay: Int = 24 * 60
  def generateToken(uuid: String, expiryTimeInMinutes: Int = oneDay): VerificationToken
  def generatePinCode(pinSize: Int, expiryTimeInMinutes: Int = 5): VerificationCode
}

trait DefaultGenerator extends Generator {

  override def generateToken(uuid: String, expiryTimeInMinutes: Int): VerificationToken =
    VerificationToken(uuid, expiryTimeInMinutes)

  override def generatePinCode(pinSize: Int, expiryTimeInMinutes: Int): VerificationCode =
    VerificationCode(pinSize, expiryTimeInMinutes)
}
