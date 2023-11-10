package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.account.handlers.BasicAccountTypeKey
import app.softnetwork.account.launch.AccountEndpoints
import app.softnetwork.account.message.BasicAccountSignUp
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.service.{
  AccountServiceEndpoints,
  BasicAccountServiceEndpoints,
  OAuthServiceEndpoints
}
import app.softnetwork.api.server.Endpoint
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.service.SessionEndpoints
import org.slf4j.{Logger, LoggerFactory}

trait BasicAccountEndpoints
    extends AccountEndpoints[BasicAccount, BasicAccountProfile, BasicAccountSignUp] {
  self: BasicAccountApi with SchemaProvider with CsrfCheck =>
  override def accountEndpoints: ActorSystem[_] => AccountServiceEndpoints[BasicAccountSignUp] =
    system => BasicAccountServiceEndpoints(system, sessionEndpoints(system))

  override def oauthEndpoints: ActorSystem[_] => OAuthServiceEndpoints =
    sys =>
      new OAuthServiceEndpoints with BasicAccountTypeKey {
        override def sessionEndpoints: SessionEndpoints = self.sessionEndpoints(system)
        override implicit def system: ActorSystem[_] = sys
        override def log: Logger = LoggerFactory getLogger getClass.getName
      }

  override def endpoints: ActorSystem[_] => List[Endpoint] = system =>
    super.endpoints(system) :+ accountSwagger(system) :+ oauthSwagger(system)
}
