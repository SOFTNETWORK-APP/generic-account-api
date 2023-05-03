package app.softnetwork.account.handlers

import org.slf4j.{Logger, LoggerFactory}

object MockBasicAccountDao extends AccountDao with AccountHandler with MockBasicAccountTypeKey {
  lazy val log: Logger = LoggerFactory getLogger getClass.getName
}
