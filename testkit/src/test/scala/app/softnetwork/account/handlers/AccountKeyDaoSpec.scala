package app.softnetwork.account.handlers

import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.account.scalatest.BasicAccountTestKit
import AccountKeyDao._
import akka.actor.typed.ActorSystem
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import app.softnetwork.session.service.BasicSessionMaterials
import com.softwaremill.session.RefreshTokenStorage
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

/** Created by smanciot on 19/04/2020.
  */
class AccountKeyDaoSpec
    extends AnyWordSpecLike
    with BasicAccountTestKit
    with BasicSessionMaterials[Session] {

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override implicit def ts: ActorSystem[_] = tsystem

  override implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(
    ts
  )

  "AccountKey" must {
    "add key" in {
      addAccountKey("add", "account")
      lookupAccount("add") await {
        case Some(account) => account shouldBe "account"
        case _             => fail()
      }
    }

    "remove key" in {
      addAccountKey("remove", "account")
      removeAccountKey("remove")
      lookupAccount("remove") await {
        case Some(_) => fail()
        case _       => succeed
      }
    }
  }

}
