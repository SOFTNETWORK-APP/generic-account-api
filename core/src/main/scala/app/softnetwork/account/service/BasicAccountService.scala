package app.softnetwork.account.service

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshaller
import app.softnetwork.account.handlers.BasicAccountTypeKey
import app.softnetwork.account.message.{BasicAccountSignUp, SignUp}
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials

trait BasicAccountService[SD <: SessionData with SessionDataDecorator[SD]]
    extends DefaultAccountService[SD]
    with BasicAccountTypeKey {
  _: SessionMaterials[SD] =>
  override type SU = BasicAccountSignUp

  override implicit def toSignUp: SU => SignUp = identity

  override def asSignUp: Unmarshaller[HttpRequest, SU] = as[BasicAccountSignUp]
}
