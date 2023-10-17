package app.softnetwork.account.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}
import app.softnetwork.account.serialization.accountFormats
import app.softnetwork.account.service.AccountServiceEndpoints
import app.softnetwork.api.server.{ApiEndpoints, Endpoint}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import org.json4s.Formats

trait AccountEndpoints[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  SU
] extends ApiEndpoints
    with AccountGuardian[T, P] { _: SchemaProvider with CsrfCheck =>

  override implicit def formats: Formats = accountFormats

  def accountEndpoints: ActorSystem[_] => AccountServiceEndpoints[SU]

  override def endpoints: ActorSystem[_] => List[Endpoint] =
    system =>
      List(
        accountEndpoints(system)
      )
}
