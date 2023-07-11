package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.account.launch.AccountEndpoints
import app.softnetwork.account.message.BasicAccountSignUp
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.service.{AccountServiceEndpoints, BasicAccountServiceEndpoints}
import app.softnetwork.persistence.schema.SchemaProvider

trait BasicAccountEndpoints
    extends AccountEndpoints[BasicAccount, BasicAccountProfile, BasicAccountSignUp] {
  _: SchemaProvider =>
  override def accountEndpoints: ActorSystem[_] => AccountServiceEndpoints[BasicAccountSignUp] =
    system => BasicAccountServiceEndpoints(system, sessionEndpoints(system))
}
