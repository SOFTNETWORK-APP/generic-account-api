package app.softnetwork.account.service

import akka.actor.typed.ActorSystem
import app.softnetwork.account.handlers.MockBasicAccountTypeKey
import app.softnetwork.session.service.SessionService
import org.slf4j.{Logger, LoggerFactory}

object MockOAuthService {
  def apply(asystem: ActorSystem[_], _sessionService: SessionService): MockOAuthService =
    new MockOAuthService {
      override def service: SessionService = _sessionService
      override implicit def system: ActorSystem[_] = asystem
      lazy val log: Logger = LoggerFactory getLogger getClass.getName
    }
}
trait MockOAuthService extends BasicOAuthService with MockBasicAccountTypeKey
