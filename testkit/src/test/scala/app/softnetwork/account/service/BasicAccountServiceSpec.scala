package app.softnetwork.account.service

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshaller
import app.softnetwork.account.message._
import app.softnetwork.account.model._
import app.softnetwork.account.scalatest.{AccountServiceSpec, BasicAccountTestKit}
import app.softnetwork.session.service.{
  OneOffCookieSessionService,
  OneOffHeaderSessionService,
  RefreshableCookieSessionService,
  RefreshableHeaderSessionService,
  SessionService
}
import org.slf4j.{Logger, LoggerFactory}

/** Created by smanciot on 18/04/2020.
  */
trait BasicAccountServiceSpec
    extends AccountServiceSpec[
      BasicAccount,
      BasicAccountProfile,
      DefaultProfileView,
      DefaultAccountDetailsView,
      DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView]
    ]
    with MockBasicAccountService
    with BasicAccountTestKit {

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override def asSignUp: Unmarshaller[HttpRequest, SU] = as[BasicAccountSignUp]

}

class OneOfCookieSessionBasicAccountServiceSpec extends BasicAccountServiceSpec {
  override def service: SessionService = OneOffCookieSessionService(system)
  override protected val manifestWrapper: ManifestW = ManifestW()
}

class OneOfHeaderSessionBasicAccountServiceSpec extends BasicAccountServiceSpec {
  override def service: SessionService = OneOffHeaderSessionService(system)
  override protected val manifestWrapper: ManifestW = ManifestW()
}

class RefreshableCookieSessionBasicAccountServiceSpec extends BasicAccountServiceSpec {
  override def service: SessionService = RefreshableCookieSessionService(system)
  override protected val manifestWrapper: ManifestW = ManifestW()
}

class RefreshableHeaderSessionBasicAccountServiceSpec extends BasicAccountServiceSpec {
  override def service: SessionService = RefreshableHeaderSessionService(system)
  override protected val manifestWrapper: ManifestW = ManifestW()
}
