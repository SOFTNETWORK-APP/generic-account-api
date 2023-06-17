package app.softnetwork.account.service

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshaller
import app.softnetwork.account.message._
import app.softnetwork.account.model._
import app.softnetwork.account.scalatest.{AccountServiceSpec, BasicAccountTestKit}
import app.softnetwork.session.service.SessionService
import org.slf4j.{Logger, LoggerFactory}

/** Created by smanciot on 18/04/2020.
  */
trait BasicAccountServiceSpec
    extends AccountServiceSpec[BasicAccount, BasicAccountProfile]
    with MockBasicAccountService
    with BasicAccountTestKit {

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override def asSignUp: Unmarshaller[HttpRequest, SU] = as[BasicAccountSignUp]

}

class OneOfCookieSessionBasicAccountServiceSpec extends BasicAccountServiceSpec {
  override def sessionService: SessionService = SessionService.oneOffCookie(system)
}

class OneOfHeaderSessionBasicAccountServiceSpec extends BasicAccountServiceSpec {
  override def sessionService: SessionService = SessionService.oneOffHeader(system)
}

class RefreshableCookieSessionBasicAccountServiceSpec extends BasicAccountServiceSpec {
  override def sessionService: SessionService = SessionService.refreshableCookie(system)
}

class RefreshableHeaderSessionBasicAccountServiceSpec extends BasicAccountServiceSpec {
  override def sessionService: SessionService = SessionService.refreshableHeader(system)
}
