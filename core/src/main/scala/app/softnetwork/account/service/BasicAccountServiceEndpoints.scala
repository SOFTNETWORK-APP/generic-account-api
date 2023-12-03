package app.softnetwork.account.service

import app.softnetwork.account.handlers.BasicAccountTypeKey
import app.softnetwork.account.message.{BasicAccountSignUp, SignUp}
import app.softnetwork.account.model.{
  DefaultAccountDetailsView,
  DefaultAccountView,
  DefaultProfileView
}
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import sttp.tapir.Schema

trait BasicAccountServiceEndpoints[SD <: SessionData with SessionDataDecorator[SD]]
    extends AccountServiceEndpoints[BasicAccountSignUp, SD]
    with BasicAccountTypeKey { _: SessionMaterials[SD] =>

  override implicit def toSignUp: BasicAccountSignUp => SignUp = identity

  override implicit val SUS: Schema[BasicAccountSignUp] = Schema.derived

  override type PV = DefaultProfileView

  override type DV = DefaultAccountDetailsView

  override type AV = DefaultAccountView[PV, DV]

  override implicit def AVSchema: Schema[AV] = Schema.derived
}
