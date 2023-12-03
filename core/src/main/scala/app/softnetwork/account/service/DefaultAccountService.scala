package app.softnetwork.account.service

import app.softnetwork.account.message.AccountCommand
import app.softnetwork.account.model.{
  DefaultAccountDetailsView,
  DefaultAccountView,
  DefaultProfileView
}
import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials

trait DefaultAccountService[SD <: SessionData with SessionDataDecorator[SD]]
    extends AccountService[
      DefaultProfileView,
      DefaultAccountDetailsView,
      DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView],
      SD
    ]
    with ManifestWrapper[DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]] {
  _: CommandTypeKey[AccountCommand] with SessionMaterials[SD] =>

}
