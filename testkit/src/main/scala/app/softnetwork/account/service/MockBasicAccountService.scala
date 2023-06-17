package app.softnetwork.account.service

import akka.actor.typed.ActorSystem
import app.softnetwork.account.handlers.MockBasicAccountTypeKey
import app.softnetwork.session.service.SessionService
import org.slf4j.{Logger, LoggerFactory}

object MockBasicAccountService {
  def apply(asystem: ActorSystem[_], _sessionService: SessionService): MockBasicAccountService = {
    new MockBasicAccountService {
      lazy val log: Logger = LoggerFactory getLogger getClass.getName
      override implicit def system: ActorSystem[_] = asystem
      override def sessionService: SessionService = _sessionService
    }
  }
}

trait MockBasicAccountService extends BasicAccountService with MockBasicAccountTypeKey
