package app.softnetwork.account.service

import app.softnetwork.account.handlers.AccountHandler
import app.softnetwork.account.message.{AccountCommand, AccountCommandResult}
import app.softnetwork.persistence.service.Service
import app.softnetwork.persistence.typed.CommandTypeKey

import java.net.URLEncoder

trait BaseAccountService extends Service[AccountCommand, AccountCommandResult] with AccountHandler {
  _: CommandTypeKey[AccountCommand] =>

  protected def redirection(uri: String, params: Map[String, String]): String = {
    var redirection = uri
    if (uri.contains("?")) {
      redirection += "&"
    } else {
      redirection += "?"
    }
    var first = true
    params.foreach { case (key, value) =>
      redirection += (if (!first) "&" else "") + URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder
        .encode(value, "UTF-8")
      if (first) first = false
    }
    redirection
  }
}
