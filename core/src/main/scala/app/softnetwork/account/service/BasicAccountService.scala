package app.softnetwork.account.service

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshaller
import app.softnetwork.account.handlers.BasicAccountTypeKey
import app.softnetwork.account.message.{BasicAccountSignUp, SignUp}
import app.softnetwork.session.service.SessionMaterials

trait BasicAccountService extends DefaultAccountService with BasicAccountTypeKey {
  _: SessionMaterials =>
  override type SU = BasicAccountSignUp

  override implicit def toSignUp: SU => SignUp = identity

  override def asSignUp: Unmarshaller[HttpRequest, SU] = as[BasicAccountSignUp]
}
