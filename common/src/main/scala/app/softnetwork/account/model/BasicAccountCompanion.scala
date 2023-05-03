package app.softnetwork.account.model

import app.softnetwork.security.Sha512Encryption._
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.message.{InitAdminAccount, SignUp}
import app.softnetwork.persistence._

import java.time.Instant

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
          .withCreatedDate(Instant.now())
          .withLastUpdated(Instant.now())
          .withPrincipal(principal)
          .withCredentials(encrypt(password))
          .withStatus(status)
          .add(principal)
      if (admin) {
        Some(
          basicAccount
            .add(
              BasicAccountProfile.defaultInstance
                .withName("admin")
                .withType(ProfileType.ADMINISTRATOR)
                .withFirstName(profile.map(_.firstName).getOrElse("admin"))
                .withLastName(profile.map(_.lastName).getOrElse(""))
            )
            .asInstanceOf[BasicAccount]
        )
      } else {
        val account =
          profile match {
            case Some(p) =>
              val basicProfile =
                basicAccount
                  .completeProfile(
                    BasicAccountProfile.defaultInstance.copy(
                      name = p.name,
                      `type` = p.`type`,
                      description = p.description,
                      firstName = p.firstName,
                      lastName = p.lastName,
                      phoneNumber = p.phoneNumber,
                      email = p.email,
                      userName = p.userName
                    )
                  )
                  .asInstanceOf[BasicAccountProfile]
              p.userName match {
                case Some(value) => basicAccount.add(Principal(value)).add(basicProfile)
                case _           => basicAccount.add(basicProfile)
              }
            case _ => basicAccount
          }
        Some(
          account.asInstanceOf[BasicAccount]
        )
      }
    }
  }

}
