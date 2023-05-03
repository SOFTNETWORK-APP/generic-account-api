package app.softnetwork.account.service

import akka.actor.typed.ActorSystem
import app.softnetwork.account.handlers.MockBasicAccountTypeKey
import org.slf4j.{Logger, LoggerFactory}

object MockBasicAccountService {
  def apply(asystem: ActorSystem[_]): MockBasicAccountService = {
    new MockBasicAccountService {
      lazy val log: Logger = LoggerFactory getLogger getClass.getName
      override implicit def system: ActorSystem[_] = asystem
    }
  }
}

trait MockBasicAccountService extends BasicAccountService with MockBasicAccountTypeKey
