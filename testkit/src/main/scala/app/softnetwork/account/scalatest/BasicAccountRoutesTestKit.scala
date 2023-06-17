package app.softnetwork.account.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.account.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.account.service.{AccountService, MockBasicAccountService}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.scalatest.{OneOffCookieSessionServiceTestKit, OneOffHeaderSessionServiceTestKit}
import org.scalatest.Suite

trait BasicAccountRoutesTestKit extends AccountRoutesTestKit[BasicAccount, BasicAccountProfile] {
  _: SchemaProvider =>

  override def accountService: ActorSystem[_] => AccountService = system =>
    MockBasicAccountService(system, sessionService(system))

}

trait OneOfCookieSessionBasicAccountRoutesTestKit extends OneOffCookieSessionServiceTestKit
  with BasicAccountRoutesTestKit{_: Suite =>}

trait OneOfHeaderSessionBasicAccountRoutesTestKit extends OneOffHeaderSessionServiceTestKit
  with BasicAccountRoutesTestKit{_: Suite =>}
