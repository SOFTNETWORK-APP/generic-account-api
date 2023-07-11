package app.softnetwork.account.service

import akka.actor.typed.ActorSystem
import app.softnetwork.account.handlers.BasicAccountTypeKey
import app.softnetwork.account.message.{BasicAccountSignUp, SignUp}
import app.softnetwork.session.service.SessionEndpoints
import org.slf4j.{Logger, LoggerFactory}
import sttp.tapir.generic.auto._
import sttp.tapir.Schema

trait BasicAccountServiceEndpoints
    extends AccountServiceEndpoints[BasicAccountSignUp]
    with BasicAccountTypeKey {

  override implicit def toSignUp: BasicAccountSignUp => SignUp = identity

  override implicit val SUS: Schema[BasicAccountSignUp] = Schema.derived
}

object BasicAccountServiceEndpoints {
  def apply(
    _system: ActorSystem[_],
    _sessionEndpoints: SessionEndpoints
  ): BasicAccountServiceEndpoints = {
    new BasicAccountServiceEndpoints {
      lazy val log: Logger = LoggerFactory getLogger getClass.getName
      override implicit def system: ActorSystem[_] = _system
      override def sessionEndpoints: SessionEndpoints = _sessionEndpoints
      override protected val manifestWrapper: ManifestW = ManifestW()
    }
  }
}
