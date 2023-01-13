package app.softnetwork.account.persistence.typed

import java.util.Date
import akka.actor.typed.ActorSystem
import app.softnetwork.concurrent.Completion
import mustache.Mustache
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.Logger
import app.softnetwork.notification.model._
import app.softnetwork.notification.serialization._
import app.softnetwork.account.config.AccountSettings._
import app.softnetwork.notification.message.{
  AddMailCommandEvent,
  AddPushCommandEvent,
  AddSMSCommandEvent,
  ExternalEntityToNotificationEvent
}
import app.softnetwork.account.model._

import scala.language.{implicitConversions, postfixOps}

trait AccountNotifications[T <: Account] extends Completion {

  /** number of login failures authorized before disabling user account * */
  val maxLoginFailures: Int = MaxLoginFailures

  protected def activationTokenUuid(entityId: String): String = {
    s"$entityId-activation-token"
  }

  protected def registrationUuid(entityId: String): String = {
    s"$entityId-registration"
  }

  private[this] def addMail(
    uuid: String,
    account: T,
    subject: String,
    body: String,
    maxTries: Int,
    deferred: Option[Date]
  )(implicit system: ActorSystem[_]): Option[ExternalEntityToNotificationEvent] = {
    account.email match {
      case Some(email) =>
        Some(
          ExternalEntityToNotificationEvent(
            ExternalEntityToNotificationEvent.Wrapped.AddMail(
              AddMailCommandEvent(
                Mail.defaultInstance
                  .withUuid(uuid)
                  .withFrom(From(MailFrom, Some(MailName)))
                  .withTo(Seq(email))
                  .withSubject(subject)
                  .withMessage(StringEscapeUtils.unescapeHtml4(body).replaceAll("<br/>", "\\\n"))
                  .withRichMessage(body)
                  .withMaxTries(maxTries)
                  .withDeferred(deferred.orNull)
              )
            )
          )
        )
      case _ => None
    }
  }

  private[this] def addSMS(
    uuid: String,
    account: T,
    subject: String,
    body: String,
    maxTries: Int,
    deferred: Option[Date]
  )(implicit system: ActorSystem[_]): Option[ExternalEntityToNotificationEvent] = {
    account.gsm match {
      case Some(gsm) =>
        Some(
          ExternalEntityToNotificationEvent(
            ExternalEntityToNotificationEvent.Wrapped.AddSMS(
              AddSMSCommandEvent(
                SMS.defaultInstance
                  .withUuid(uuid)
                  .withFrom(From(SMSClientId, Some(SMSName)))
                  .withTo(Seq(gsm))
                  .withSubject(subject)
                  .withMessage(StringEscapeUtils.unescapeHtml4(body).replaceAll("<br/>", "\\\n"))
                  .withMaxTries(maxTries)
                  .withDeferred(deferred.orNull)
              )
            )
          )
        )
      case _ => None
    }
  }

  private[this] def addPush(
    uuid: String,
    account: T,
    subject: String,
    body: String,
    maxTries: Int,
    deferred: Option[Date]
  )(implicit system: ActorSystem[_]): Option[ExternalEntityToNotificationEvent] = {
    if (account.registrations.isEmpty) {
      None
    } else {
      Some(
        ExternalEntityToNotificationEvent(
          ExternalEntityToNotificationEvent.Wrapped.AddPush(
            AddPushCommandEvent(
              Push.defaultInstance
                .withUuid(uuid)
                .withFrom(From.defaultInstance.withValue(PushClientId))
                .withSubject(subject)
                .withMessage(StringEscapeUtils.unescapeHtml4(body).replaceAll("<br/>", "\\\n"))
                .withDevices(
                  account.registrations.map(registration =>
                    BasicDevice(registration.regId, registration.platform)
                  )
                )
                .withMaxTries(maxTries)
                .withDeferred(deferred.orNull)
            )
          )
        )
      )
    }
  }

  private[this] def addNotificationByChannel(
    uuid: String,
    account: T,
    subject: String,
    body: String,
    channel: NotificationType,
    maxTries: Int,
    deferred: Option[Date]
  )(implicit system: ActorSystem[_]): Option[ExternalEntityToNotificationEvent] = {
    channel match {
      case NotificationType.MAIL_TYPE =>
        addMail(s"mail#$uuid", account, subject, body, maxTries, deferred)
      case NotificationType.SMS_TYPE =>
        addSMS(s"sms#$uuid", account, subject, body, maxTries, deferred)
      case NotificationType.PUSH_TYPE =>
        addPush(s"push#$uuid", account, subject, body, maxTries, deferred)
      case _ => None
    }
  }

  /** @param uuid
    *   - unique user id
    * @param account
    *   - account
    * @param subject
    *   - subject of the message to send
    * @param body
    *   - body of the message to send
    * @param channels
    *   - channels to which the message should be sent
    * @param maxTries
    *   - maximum of tries
    * @param deferred
    *   - whether the message has to be sent at the specified date or not
    * @return
    *   true if the message has been successfully added to at least one of the channels, false
    *   otherwise
    */
  def addNotifications(
    uuid: String,
    account: T,
    subject: String,
    body: String,
    channels: Seq[NotificationType],
    maxTries: Int = 1,
    deferred: Option[Date] = None
  )(implicit log: Logger, system: ActorSystem[_]): Seq[ExternalEntityToNotificationEvent] = {
    log.info(
      s"about to send notification to ${account.primaryPrincipal.value} for channels [${channels
        .mkString(",")}]\r\n$body"
    )
    val notifications = channels.flatMap(channel =>
      addNotificationByChannel(uuid, account, subject, body, channel, maxTries, deferred)
    )
    for (notification <- notifications) {
      if (notification.wrapped.isAddMail) {
        log.info(s"add mail to ${account.primaryPrincipal.value}")
      } else if (notification.wrapped.isAddSMS) {
        log.info(s"add sms to ${account.primaryPrincipal.value}")
      } else if (notification.wrapped.isAddPush) {
        log.info(s"add push to ${account.primaryPrincipal.value}")
      }
    }
    notifications
  }

  def sendActivation(
    uuid: String,
    account: T,
    activationToken: VerificationToken,
    maxTries: Int = 3,
    deferred: Option[Date] = None
  )(implicit log: Logger, system: ActorSystem[_]): Seq[ExternalEntityToNotificationEvent] = {
    val subject = NotificationsConfig.activation

    val body = Mustache("notification/activation.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "activationUrl" -> s"$BaseUrl/$Path/activate/${activationToken.token}",
        "signature"     -> NotificationsConfig.signature
      )
    )

    addNotifications(
      activationTokenUuid(uuid),
      account,
      subject,
      body,
      NotificationsConfig.channels.activation,
      maxTries,
      deferred
    )
  }

  def sendRegistration(uuid: String, account: T, maxTries: Int = 2, deferred: Option[Date] = None)(
    implicit
    log: Logger,
    system: ActorSystem[_]
  ): Seq[ExternalEntityToNotificationEvent] = {
    val subject = NotificationsConfig.registration

    val body = Mustache("notification/registration.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "signature" -> NotificationsConfig.signature
      )
    )

    addNotifications(
      registrationUuid(uuid),
      account,
      subject,
      body,
      NotificationsConfig.channels.registration,
      maxTries,
      deferred
    )
  }

  def sendVerificationCode(
    uuid: String,
    account: T,
    verificationCode: VerificationCode,
    maxTries: Int = 1,
    deferred: Option[Date] = None
  )(implicit log: Logger, system: ActorSystem[_]): Seq[ExternalEntityToNotificationEvent] = {
    val subject = NotificationsConfig.resetPassword

    val body = Mustache("notification/verification_code.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "code"      -> verificationCode.code,
        "signature" -> NotificationsConfig.signature
      )
    )

    addNotifications(
      uuid,
      account,
      subject,
      body,
      NotificationsConfig.channels.resetPassword,
      maxTries,
      deferred
    )
  }

  def sendAccountDisabled(
    uuid: String,
    account: T,
    maxTries: Int = 1,
    deferred: Option[Date] = None
  )(implicit log: Logger, system: ActorSystem[_]): Seq[ExternalEntityToNotificationEvent] = {
    val subject = NotificationsConfig.accountDisabled

    val body = Mustache("notification/account_disabled.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "resetPasswordUrl" -> ResetPasswordUrl,
        "loginFailures"    -> (maxLoginFailures + 1),
        "signature"        -> NotificationsConfig.signature
      )
    )

    addNotifications(
      uuid,
      account,
      subject,
      body,
      NotificationsConfig.channels.accountDisabled,
      maxTries,
      deferred
    )
  }

  def sendResetPassword(
    uuid: String,
    account: T,
    verificationToken: VerificationToken,
    maxTries: Int = 1,
    deferred: Option[Date] = None
  )(implicit log: Logger, system: ActorSystem[_]): Seq[ExternalEntityToNotificationEvent] = {
    val subject = NotificationsConfig.resetPassword

    val body = Mustache("notification/reset_password.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "token"            -> verificationToken.token,
        "principal"        -> account.principal.value,
        "resetPasswordUrl" -> ResetPasswordUrl,
        "signature"        -> NotificationsConfig.signature
      )
    )

    addNotifications(
      uuid,
      account,
      subject,
      body,
      NotificationsConfig.channels.resetPassword,
      maxTries,
      deferred
    )
  }

  def sendPasswordUpdated(
    uuid: String,
    account: T,
    maxTries: Int = 1,
    deferred: Option[Date] = None
  )(implicit log: Logger, system: ActorSystem[_]): Seq[ExternalEntityToNotificationEvent] = {
    val subject = NotificationsConfig.passwordUpdated

    val body = Mustache("notification/password_updated.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "signature" -> NotificationsConfig.signature
      )
    )

    addNotifications(
      uuid,
      account,
      subject,
      body,
      NotificationsConfig.channels.passwordUpdated,
      maxTries,
      deferred
    )
  }

  def sendPrincipalUpdated(
    uuid: String,
    account: T,
    maxTries: Int = 1,
    deferred: Option[Date] = None
  )(implicit log: Logger, system: ActorSystem[_]): Seq[ExternalEntityToNotificationEvent] = {
    val subject = NotificationsConfig.principalUpdated

    val body = Mustache("notification/principal_updated.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "signature" -> NotificationsConfig.signature
      )
    )

    addNotifications(
      uuid,
      account,
      subject,
      body,
      NotificationsConfig.channels.principalUpdated,
      maxTries,
      deferred
    )
  }

}
