package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.account.launch.AccountEndpoints
import app.softnetwork.account.message.BasicAccountSignUp
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.service.{AccountServiceEndpoints, BasicAccountServiceEndpoints}
import app.softnetwork.api.server.Endpoint
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck

trait BasicAccountEndpoints
    extends AccountEndpoints[BasicAccount, BasicAccountProfile, BasicAccountSignUp] {
  _: BasicAccountApi with SchemaProvider with CsrfCheck =>
  override def accountEndpoints: ActorSystem[_] => AccountServiceEndpoints[BasicAccountSignUp] =
    system => BasicAccountServiceEndpoints(system, sessionEndpoints(system))

  override def endpoints: ActorSystem[_] => List[Endpoint] = system =>
    super.endpoints(system) :+ accountSwagger(system)
}
