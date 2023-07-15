package app.softnetwork.account.service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshaller
import app.softnetwork.account.handlers.BasicAccountTypeKey
import app.softnetwork.account.message.{BasicAccountSignUp, SignUp}
import app.softnetwork.session.service.SessionService
import org.slf4j.{Logger, LoggerFactory}

trait BasicAccountService extends DefaultAccountService with BasicAccountTypeKey {
  override type SU = BasicAccountSignUp

  override implicit def toSignUp: SU => SignUp = identity

  override def asSignUp: Unmarshaller[HttpRequest, SU] = as[BasicAccountSignUp]
}

object BasicAccountService {
  def apply(asystem: ActorSystem[_], _sessionService: SessionService): BasicAccountService = {
    new BasicAccountService {
      lazy val log: Logger = LoggerFactory getLogger getClass.getName
      override implicit def system: ActorSystem[_] = asystem
      override def sessionService: SessionService = _sessionService
      override protected lazy val manifestWrapper: ManifestW = ManifestW()
    }
  }
}
