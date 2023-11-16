package app.softnetwork.account.service

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.`WWW-Authenticate`
import akka.http.scaladsl.server.AuthenticationFailedRejection.{
  CredentialsMissing,
  CredentialsRejected
}
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directives, Route}
import akka.http.scaladsl.server.directives.Credentials
import app.softnetwork.account.message.{
  AccountCommand,
  AccountCommandResult,
  BasicAuth,
  LoginSucceededResult,
  OAuth
}
import app.softnetwork.account.model.Account
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.session.service.{ServiceWithSessionDirectives, SessionMaterials}

import scala.concurrent.Future

trait AccountServiceDirectives
    extends BaseAccountService
    with ServiceWithSessionDirectives[AccountCommand, AccountCommandResult]
    with Directives {
  _: CommandTypeKey[AccountCommand] with SessionMaterials =>

  protected def basicAuth: Credentials => Future[Option[Account]] = {
    case p @ Credentials.Provided(_) =>
      run(p.identifier, BasicAuth(p)) map {
        case r: LoginSucceededResult => Some(r.account)
        case _                       => None
      }
    case _ => Future.successful(None)
  }

  protected def oauth: Credentials => Future[Option[Account]] = {
    case _ @Credentials.Provided(token) =>
      run(token, OAuth(token)) map {
        case r: LoginSucceededResult => Some(r.account)
        case _                       => None
      }
    case _ => Future.successful(None)
  }

  protected def authenticationFailedRejectionHandler
    : scala.collection.immutable.Seq[AuthenticationFailedRejection] => Route = rejections => {
    val rejectionMessage = rejections.head.cause match {
      case CredentialsMissing =>
        "The resource requires authentication, which was not supplied with the request"
      case CredentialsRejected => "The supplied authentication is invalid"
    }
    // Multiple challenges per WWW-Authenticate header are allowed per spec,
    // however, it seems many browsers will ignore all challenges but the first.
    // Therefore, multiple WWW-Authenticate headers are rendered, instead.
    //
    // See https://code.google.com/p/chromium/issues/detail?id=103220
    // and https://bugzilla.mozilla.org/show_bug.cgi?id=669675
    val authenticateHeaders = rejections.map(r => `WWW-Authenticate`(r.challenge))
    extractRequest { request =>
      extractMaterializer { implicit mat =>
        request.discardEntityBytes()
        complete(StatusCodes.Unauthorized, authenticateHeaders, rejectionMessage)
      }
    }
  }

}
