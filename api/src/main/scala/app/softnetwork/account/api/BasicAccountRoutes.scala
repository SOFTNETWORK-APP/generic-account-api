package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.account.launch.AccountRoutes
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.service.{AccountService, BasicAccountService}
import app.softnetwork.persistence.schema.SchemaProvider

trait BasicAccountRoutes extends AccountRoutes[BasicAccount, BasicAccountProfile] {
  _: SchemaProvider =>
  override def accountService: ActorSystem[_] => AccountService =
    sys => BasicAccountService(sys, sessionService(sys))
}
