package app.softnetwork.account.service

import app.softnetwork.account.message.AccountCommand
import app.softnetwork.account.model.{
  DefaultAccountDetailsView,
  DefaultAccountView,
  DefaultProfileView
}
import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.session.service.SessionMaterials

trait DefaultAccountService
    extends AccountService[
      DefaultProfileView,
      DefaultAccountDetailsView,
      DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]
    ]
    with ManifestWrapper[DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]] {
  _: CommandTypeKey[AccountCommand] with SessionMaterials =>

}
