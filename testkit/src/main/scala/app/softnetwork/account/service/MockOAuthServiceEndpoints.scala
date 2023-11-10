package app.softnetwork.account.service

import akka.actor.typed.ActorSystem
import app.softnetwork.account.handlers.MockBasicAccountTypeKey
import app.softnetwork.session.service.SessionEndpoints
import org.slf4j.{Logger, LoggerFactory}

trait MockOAuthServiceEndpoints extends OAuthServiceEndpoints with MockBasicAccountTypeKey

object MockOAuthServiceEndpoints {
  def apply(
    _system: ActorSystem[_],
    _sessionEndpoints: SessionEndpoints
  ): MockOAuthServiceEndpoints = {
    new MockOAuthServiceEndpoints {
      lazy val log: Logger = LoggerFactory getLogger getClass.getName
      override implicit def system: ActorSystem[_] = _system
      override def sessionEndpoints: SessionEndpoints = _sessionEndpoints
    }
  }
}
