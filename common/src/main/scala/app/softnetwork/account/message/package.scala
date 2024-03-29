package app.softnetwork.account

import akka.http.scaladsl.server.directives.Credentials
import app.softnetwork.persistence.message._
import app.softnetwork.notification.model.NotificationType
import app.softnetwork.account.model._
import app.softnetwork.scheduler.model.Schedule

/** Created by smanciot on 17/04/2020.
  */
package object message {

  /** Created by smanciot on 19/03/2018.
    */
  /*sealed */
  trait AccountCommand extends Command

  trait SignUp extends AccountCommand {
    def login: String
    def password: String
    def confirmPassword: Option[String] = None
    def profile: Option[Profile] = None
  }

  case class BasicAccountSignUp(
    login: String,
    password: String,
    override val confirmPassword: Option[String] = None,
    override val profile: Option[BasicAccountProfile] = None
  ) extends SignUp

  case object SignUpAnonymous extends AccountCommand

  case class SignUpOAuth(data: OAuthData) extends AccountCommand

  @SerialVersionUID(0L)
  case class Unsubscribe(uuid: String) extends AccountCommand

  case object DestroyAccount extends AccountCommand

  trait LookupAccountCommand extends AccountCommand

  case class BasicAuth(credentials: Credentials.Provided) extends LookupAccountCommand

  case class GenerateAuthorizationCode(
    clientId: String,
    scope: Option[String] = None,
    redirectUri: Option[String] = None,
    state: Option[String] = None
  ) extends AccountCommand

  case class GenerateAccessToken(
    clientId: String,
    code: String,
    redirectUri: Option[String] = None
  ) extends LookupAccountCommand

  case class RefreshAccessToken(refreshToken: String) extends LookupAccountCommand

  case class OAuth(token: String) extends LookupAccountCommand

  @SerialVersionUID(0L)
  case class Login(
    login: String,
    password: String,
    refreshable: Boolean = false,
    anonymous: Option[String] = None
  ) extends LookupAccountCommand

  case object Logout extends AccountCommand

  @SerialVersionUID(0L)
  case class SendResetPasswordToken(principal: String) extends LookupAccountCommand

  @SerialVersionUID(0L)
  case class SendVerificationCode(principal: String) extends LookupAccountCommand

  @SerialVersionUID(0L)
  case class CheckResetPasswordToken(token: String) extends LookupAccountCommand

  @SerialVersionUID(0L)
  case class ResetPassword(
    token: String,
    newPassword: String,
    confirmedPassword: Option[String] = None
  ) extends LookupAccountCommand

  @SerialVersionUID(0L)
  case class PasswordData(
    oldPassword: String,
    newPassword: String,
    confirmedPassword: Option[String] = None
  )

  @SerialVersionUID(0L)
  case class UpdatePassword(
    login: String,
    oldPassword: String,
    newPassword: String,
    confirmedPassword: Option[String] = None
  ) extends AccountCommand

  @SerialVersionUID(0L)
  case class Activate(token: String) extends LookupAccountCommand

  sealed trait DeviceCommand extends AccountCommand

  @SerialVersionUID(0L)
  case class RegisterDevice(uuid: String, registration: DeviceRegistration) extends DeviceCommand

  @SerialVersionUID(0L)
  case class UnregisterDevice(uuid: String, regId: String, deviceId: Option[String] = None)
      extends DeviceCommand

  @SerialVersionUID(0L)
  case class UpdateProfile(uuid: String, profile: Profile) extends AccountCommand

  @SerialVersionUID(0L)
  case class SwitchProfile(uuid: String, name: String) extends AccountCommand

  @SerialVersionUID(0L)
  case class LoadProfile(uuid: String, name: Option[String]) extends AccountCommand

  @SerialVersionUID(0L)
  case class UpdateLogin(oldLogin: String, newLogin: String, password: String)
      extends AccountCommand

  @SerialVersionUID(0L)
  case class InitAdminAccount(login: String, password: String) extends SignUp

  case class RecordNotification(
    uuids: Set[String],
    channel: NotificationType,
    subject: String,
    content: String
  ) extends AccountCommand

  case class WrapInternalAccountEvent(event: InternalAccountEvent) extends AccountCommand

  case class TriggerSchedule4Account(schedule: Schedule) extends AccountCommand with EntityCommand {
    override val id: String = schedule.entityId
  }

  /** Created by smanciot on 19/03/2018.
    */
  trait AccountCommandResult extends CommandResult

  case object LogoutSucceeded extends AccountCommandResult

  case object VerificationCodeSent extends AccountCommandResult

  case object ResetPasswordTokenSent extends AccountCommandResult

  case object ResetPasswordTokenChecked extends AccountCommandResult

  @SerialVersionUID(0L)
  case class PasswordReseted(uuid: String) extends AccountCommandResult

  sealed trait DeviceCommandResult extends AccountCommandResult

  case object DeviceRegistered extends DeviceCommandResult

  case object DeviceUnregistered extends DeviceCommandResult

  case object ProfileUpdated extends AccountCommandResult

  @SerialVersionUID(0L)
  case class ProfileSwitched(profile: Option[Profile]) extends AccountCommandResult

  @SerialVersionUID(0L)
  case class ProfileLoaded(profile: Profile) extends AccountCommandResult

  case object AdminAccountInitialized extends AccountCommandResult

  case class LoginSucceededResult(account: Account) extends AccountCommandResult

  case class AccountCreated(account: Account) extends AccountCommandResult

  case class AccountActivated(account: Account) extends AccountCommandResult

  case class AccountDeleted(account: Account) extends AccountCommandResult

  case class AccountDestroyed(uuid: String) extends AccountCommandResult

  case class PasswordUpdated(account: Account) extends AccountCommandResult

  case class AccountNotificationSent(uuid: String, notificationUuid: String)
      extends AccountCommandResult

  case object LoginUpdated extends AccountCommandResult

  case object NotificationRecorded extends AccountCommandResult

  case class Schedule4AccountTriggered(schedule: Schedule) extends AccountCommandResult

  case class AuthorizationCodeGenerated(authorizationCode: AuthorizationCode)
      extends AccountCommandResult

  case class AccessTokenGenerated(accessToken: AccessToken) extends AccountCommandResult

  case class AccessTokenRefreshed(accessToken: AccessToken) extends AccountCommandResult

  case class Tokens(
    access_token: String,
    token_type: String,
    expires_in: Int,
    refresh_token: String,
    refresh_token_expires_in: Option[Int] = None
  )

  case class OAuthSucceededResult(account: Account, application: Application)
      extends AccountCommandResult

  case class Me(firstName: String, lastName: String, email: Option[String] = None)

  /** Created by smanciot on 19/03/2018.
    */
  @SerialVersionUID(0L)
  class AccountErrorMessage(override val message: String)
      extends ErrorMessage(message)
      with AccountCommandResult

  case object LoginAlreadyExists extends AccountErrorMessage("LoginAlreadyExists")

  case object LoginUnaccepted extends AccountErrorMessage("LoginUnaccepted")

  case object AccountDisabled extends AccountErrorMessage("AccountDisabled")

  case object PasswordsNotMatched extends AccountErrorMessage("PasswordsNotMatched")

  case object LoginAndPasswordNotMatched extends AccountErrorMessage("LoginAndPasswordNotMatched")

  case object UndeliveredActivationToken extends AccountErrorMessage("UndeliveredActivationToken")

  case object NewResetPasswordTokenSent extends AccountErrorMessage("NewResetPasswordTokenSent")

  case object NewResetPasswordTokenNotSent
      extends AccountErrorMessage("NewResetPasswordTokenNotSent")

  case object UndeliveredResetPasswordToken
      extends AccountErrorMessage("UndeliveredResetPasswordToken")

  case object TokenNotFound extends AccountErrorMessage("TokenNotFound")

  case object TokenExpired extends AccountErrorMessage("TokenExpired")

  case object InvalidToken extends AccountErrorMessage("InvalidToken")

  case object AccountNotFound extends AccountErrorMessage("AccountNotFound")

  case object AccountNotCreated extends AccountErrorMessage("AccountNotCreated")

  case object AccountAlreadyExists extends AccountErrorMessage("AccountAlreadyExists")

  @SerialVersionUID(0L)
  case class InvalidPassword(errors: Seq[String] = Seq.empty)
      extends AccountErrorMessage(errors.mkString(","))

  case object IllegalStateError extends AccountErrorMessage("IllegalStateError")

  case object InvalidPrincipal extends AccountErrorMessage("InvalidPrincipal")

  case object UndeliveredVerificationCode extends AccountErrorMessage("UndeliveredVerificationCode")

  case object CodeNotFound extends AccountErrorMessage("CodeNotFound")

  case object CodeExpired extends AccountErrorMessage("CodeExpired")

  case object InvalidCode extends AccountErrorMessage("InvalidCode")

  case object InvalidState extends AccountErrorMessage("InvalidState")

  case object InvalidRedirection extends AccountErrorMessage("InvalidRedirection")

  case object DeviceRegistrationNotFound
      extends AccountErrorMessage("DeviceRegistrationNotFound")
      with DeviceCommandResult

  case object ProfileNotFound extends AccountErrorMessage("ProfileNotFound")

  case object GsmAlreadyExists extends AccountErrorMessage("GsmAlreadyExists")

  case object EmailAlreadyExists extends AccountErrorMessage("EmailAlreadyExists")

  case object UsernameAlreadyExists extends AccountErrorMessage("UsernameAlreadyExists")

  case class AccountNotificationNotSent(uuid: String, reason: Option[String])
      extends AccountErrorMessage("AccountNotificationNotSent")

  case object LoginNotUpdated extends AccountErrorMessage("LoginNotUpdated")

  case object InternalAccountEventNotHandled
      extends AccountErrorMessage("InternalAccountEventNotHandled")

  case object VerificationCodeNotSent extends AccountErrorMessage("VerificationCodeNotSent")

  case object ResetPasswordTokenNotSent extends AccountErrorMessage("ResetPasswordTokenNotSent")

  case object Schedule4AccountNotTriggered
      extends AccountErrorMessage("Schedule4AccountNotTriggered")

  case object AccessTokenAlreadyExists extends AccountErrorMessage("AccessTokenAlreadyExists")

  case object AuthorizationCodeAlreadyExists
      extends AccountErrorMessage("AuthorizationCodeAlreadyExists")

  case object ApplicationNotFound extends AccountErrorMessage("ApplicationNotFound")

  case object BasicAuthenticationFailed extends AccountErrorMessage("Basic authentication failed")

  case object BearerAuthenticationFailed extends AccountErrorMessage("Bearer authentication failed")
}
