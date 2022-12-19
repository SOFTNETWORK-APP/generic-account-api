package app.softnetwork.account.model

import app.softnetwork.security.Sha512Encryption._
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.message.{InitAdminAccount, SignUp}

import app.softnetwork.persistence._

/** Created by smanciot on 03/04/2018.
  */
trait BasicAccountCompanion {

  /** alias for email * */
  type Email = String

  /** alias for gsm * */
  type Gsm = String

  /** alias for username * */
  type Username = String

  def apply(command: SignUp): Option[BasicAccount] = {
    apply(command, None)
  }

  def apply(command: SignUp, uuid: Option[String]): Option[BasicAccount] = {
    import command._
    if (!password.equals(confirmPassword.getOrElse(password)))
      None
    else {
      val principal = Principal(login.trim)
      val admin = command match {
        case _: InitAdminAccount => true
        case _                   => false
      }
      val status =
        if (
          !admin && AccountSettings.ActivationEnabled && principal.`type` == PrincipalType.Email
        ) /* TODO Push notifications */
          AccountStatus.Inactive
        else
          AccountStatus.Active
      val basicAccount =
        BasicAccount.defaultInstance
          .withUuid(uuid.getOrElse(generateUUID()))
          .withCreatedDate(now())
          .withLastUpdated(now())
          .withPrincipal(principal)
          .withCredentials(encrypt(password))
          .withStatus(status)
          .add(principal)
      val account =
        userName match {
          case Some(s) => basicAccount.add(Principal(s))
          case _       => basicAccount
        }
      if (admin) {
        Some(
          account
            .add(
              BasicAccountProfile.defaultInstance
                .withName("admin")
                .withType(ProfileType.ADMINISTRATOR)
                .withFirstName(firstName.getOrElse("admin"))
                .withLastName(lastName.getOrElse(""))
            )
            .asInstanceOf[BasicAccount]
        )
      } else {
        Some(
          account.asInstanceOf[BasicAccount]
        )
      }
    }
  }

}

trait BasicAccountDecorator { _: BasicAccount =>
  override def newProfile(name: String): Profile =
    BasicAccountProfile.defaultInstance.withName(name)
}
