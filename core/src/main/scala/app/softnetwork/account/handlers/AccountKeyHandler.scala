package app.softnetwork.account.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.kv.handlers.{KvDao, KvHandler}
import app.softnetwork.account.persistence.typed.AccountKeyBehavior
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future

/** Created by smanciot on 17/04/2020.
  */
trait AccountKeyHandler extends KvHandler with AccountKeyBehavior

trait AccountKeyDao extends KvDao { _: KvHandler =>
  def lookupAccount(key: String)(implicit system: ActorSystem[_]): Future[Option[String]] = {
    lookupKeyValue(key)
  }

  def addAccountKey(key: String, account: String)(implicit system: ActorSystem[_]): Unit = {
    log.info(s"adding ($key, $account)")
    addKeyValue(key, account)
  }

  def removeAccountKey(key: String)(implicit system: ActorSystem[_]): Unit = {
    log.info(s"removing ($key)")
    removeKeyValue(key)
  }

}

object AccountKeyDao extends AccountKeyDao with AccountKeyHandler {
  lazy val log: Logger = LoggerFactory getLogger getClass.getName
}
