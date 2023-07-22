package app.softnetwork.account.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.account.serialization.accountFormats
import app.softnetwork.account.service.AccountServiceEndpoints
import app.softnetwork.api.server.{ApiEndpoint, ApiEndpoints}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.service.SessionEndpoints
import org.json4s.Formats

trait AccountEndpoints[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  SU
] extends ApiEndpoints
    with AccountGuardian[T, P] { _: SchemaProvider =>

  override implicit def formats: Formats = accountFormats

  def sessionEndpoints: ActorSystem[_] => SessionEndpoints

  def accountEndpoints: ActorSystem[_] => AccountServiceEndpoints[SU]

  override def endpoints: ActorSystem[_] => List[ApiEndpoint] =
    system =>
      List(
        accountEndpoints(system)
      )
}
