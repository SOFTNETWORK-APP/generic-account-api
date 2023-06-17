package app.softnetwork.account.handlers

import app.softnetwork.account.model._
import app.softnetwork.account.scalatest.{AccountHandlerSpec, BasicAccountTestKit}
import org.slf4j.{Logger, LoggerFactory}

/** Created by smanciot on 18/04/2020.
  */
class BasicAccountHandlerSpec
    extends AccountHandlerSpec[BasicAccount, BasicAccountProfile]
    with MockBasicAccountHandler
    with BasicAccountTestKit {

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

}
