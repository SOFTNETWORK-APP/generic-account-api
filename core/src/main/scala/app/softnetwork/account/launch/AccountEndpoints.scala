package app.softnetwork.account.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.account.serialization.accountFormats
import app.softnetwork.account.service.{AccountServiceEndpoints, OAuthServiceEndpoints}
import app.softnetwork.api.server.{ApiEndpoints, Endpoint}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import org.json4s.Formats

trait AccountEndpoints[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  SU,
  SD <: SessionData with SessionDataDecorator[SD]
] extends ApiEndpoints
    with AccountGuardian[T, P] { _: SchemaProvider with CsrfCheck =>

  override implicit def formats: Formats = accountFormats

  def accountEndpoints: ActorSystem[_] => AccountServiceEndpoints[SU, SD]

  def oauthEndpoints: ActorSystem[_] => OAuthServiceEndpoints[SD]

  override def endpoints: ActorSystem[_] => List[Endpoint] =
    system =>
      List(
        accountEndpoints(system),
        oauthEndpoints(system)
      )
}
