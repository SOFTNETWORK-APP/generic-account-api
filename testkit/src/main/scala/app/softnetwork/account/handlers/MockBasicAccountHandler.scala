package app.softnetwork.account.handlers

import org.slf4j.{Logger, LoggerFactory}

object MockBasicAccountHandler extends MockBasicAccountHandler {
  lazy val log: Logger = LoggerFactory getLogger getClass.getName
}

trait MockBasicAccountHandler extends AccountHandler with MockBasicAccountTypeKey
