package app.softnetwork.account.service

import akka.actor.typed.ActorSystem
import app.softnetwork.account.handlers.MockBasicAccountTypeKey
import app.softnetwork.session.service.SessionEndpoints
import org.slf4j.{Logger, LoggerFactory}

trait MockBasicAccountServiceEndpoints
    extends BasicAccountServiceEndpoints
    with MockBasicAccountTypeKey

object MockBasicAccountServiceEndpoints {
  def apply(
    _system: ActorSystem[_],
    _sessionEndpoints: SessionEndpoints
  ): MockBasicAccountServiceEndpoints = {
    new MockBasicAccountServiceEndpoints {
      lazy val log: Logger = LoggerFactory getLogger getClass.getName
      override implicit def system: ActorSystem[_] = _system
      override def sessionEndpoints: SessionEndpoints = _sessionEndpoints
      override protected val manifestWrapper: ManifestW = ManifestW()
    }
  }
}
