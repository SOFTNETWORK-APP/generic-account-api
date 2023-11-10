package app.softnetwork.account.handlers

import app.softnetwork.account.model.{
  AccessToken,
  AuthorizationCode,
  VerificationCode,
  VerificationToken
}

/** Created by smanciot on 09/04/2018.
  */
trait Generator {
  val oneDay: Int = 24 * 60
  def generateToken(uuid: String, expiryTimeInMinutes: Int = oneDay): VerificationToken
  def generatePinCode(pinSize: Int, expiryTimeInMinutes: Int = 5): VerificationCode

  def generateAuthorizationCode(
    clientId: String,
    scope: Option[String] = None,
    redirectUri: Option[String] = None,
    state: Option[String] = None
  ): AuthorizationCode

  def generateAccessToken(prefix: String, scope: Option[String] = None): AccessToken

}

trait DefaultGenerator extends Generator {

  override def generateToken(uuid: String, expiryTimeInMinutes: Int): VerificationToken =
    VerificationToken(uuid, expiryTimeInMinutes)

  override def generatePinCode(pinSize: Int, expiryTimeInMinutes: Int): VerificationCode =
    VerificationCode(pinSize, expiryTimeInMinutes)

  override def generateAuthorizationCode(
    clientId: String,
    scope: Option[String],
    redirectUri: Option[String],
    state: Option[String]
  ): AuthorizationCode =
    AuthorizationCode(clientId, scope, redirectUri, state)

  override def generateAccessToken(prefix: String, scope: Option[String] = None): AccessToken =
    AccessToken(prefix, scope)
}
