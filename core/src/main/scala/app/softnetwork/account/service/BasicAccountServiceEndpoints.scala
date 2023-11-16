package app.softnetwork.account.service

import app.softnetwork.account.handlers.BasicAccountTypeKey
import app.softnetwork.account.message.{BasicAccountSignUp, SignUp}
import app.softnetwork.account.model.{
  DefaultAccountDetailsView,
  DefaultAccountView,
  DefaultProfileView
}
import app.softnetwork.session.service.SessionMaterials
import sttp.tapir.Schema

trait BasicAccountServiceEndpoints
    extends AccountServiceEndpoints[BasicAccountSignUp]
    with BasicAccountTypeKey { _: SessionMaterials =>

  override implicit def toSignUp: BasicAccountSignUp => SignUp = identity

  override implicit val SUS: Schema[BasicAccountSignUp] = Schema.derived

  override type PV = DefaultProfileView

  override type DV = DefaultAccountDetailsView

  override type AV = DefaultAccountView[PV, DV]

  override implicit def AVSchema: Schema[AV] = Schema.derived
}
