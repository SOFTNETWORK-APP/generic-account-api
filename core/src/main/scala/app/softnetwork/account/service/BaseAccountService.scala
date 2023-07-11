package app.softnetwork.account.service

import app.softnetwork.account.handlers.AccountHandler
import app.softnetwork.account.message.{AccountCommand, AccountCommandResult}
import app.softnetwork.persistence.service.Service
import app.softnetwork.persistence.typed.CommandTypeKey

trait BaseAccountService extends Service[AccountCommand, AccountCommandResult] with AccountHandler {
  _: CommandTypeKey[AccountCommand] =>

}
