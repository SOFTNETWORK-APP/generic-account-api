package app.softnetwork.account.handlers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.notification.model.NotificationType
import app.softnetwork.account.message._
import app.softnetwork.account.persistence.typed.BasicAccountBehavior
import app.softnetwork.persistence._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.reflect.ClassTag

/** Created by smanciot on 18/04/2020.
  */
trait AccountHandler extends EntityPattern[AccountCommand, AccountCommandResult] {
  _: CommandTypeKey[AccountCommand] =>

  def accountKeyDao: AccountKeyDao = AccountKeyDao

  override protected def lookup[T](
    key: T
  )(implicit system: ActorSystem[_]): Future[Option[String]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    accountKeyDao.lookupAccount(key)
  }

  override def ??[T](key: T, command: AccountCommand)(implicit
    tTag: ClassTag[AccountCommand],
    system: ActorSystem[_]
  ): Future[AccountCommandResult] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    command match {
      case _: LookupAccountCommand =>
        lookup(key) flatMap {
          case Some(entityId) => this ? (entityId, command)
          case _ =>
            command match {
              case _: CheckResetPasswordToken => Future.successful(TokenNotFound)
              case _: RefreshAccessToken      => Future.successful(TokenNotFound)
              case _: ResetPassword           => Future.successful(CodeNotFound)
              case _: Login                   => Future.successful(LoginAndPasswordNotMatched)
              case _                          => Future.successful(AccountNotFound)
            }
        }
      case _ => this ? (key, command)
    }
  }

}

trait AccountDao { _: AccountHandler =>

  def initAdminAccount(login: String, password: String)(implicit system: ActorSystem[_]): Unit = { // FIXME
    this ! (generateUUID(Some(login)), new InitAdminAccount(login, password)) /*match {
      case AdminAccountInitialized => true
      case _ => false
    }*/
  }

  def recordNotification(
    uuids: Set[String],
    channel: NotificationType,
    subject: String,
    content: String
  )(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this ? (ALL_KEY, RecordNotification(uuids, channel, subject, content)) map {
      case NotificationRecorded => true
      case _                    => false
    }
  }
}

trait BasicAccountTypeKey extends CommandTypeKey[AccountCommand] {
  override def TypeKey(implicit tTag: ClassTag[AccountCommand]): EntityTypeKey[AccountCommand] =
    BasicAccountBehavior.TypeKey
}

object BasicAccountDao extends AccountDao with AccountHandler with BasicAccountTypeKey {
  lazy val log: Logger = LoggerFactory getLogger getClass.getName
}
