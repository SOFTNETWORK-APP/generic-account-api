package app.softnetwork.account.persistence.typed

import akka.actor.testkit.typed.scaladsl.TestProbe
import app.softnetwork.kv.message._
import app.softnetwork.account.scalatest.BasicAccountTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.persistence.message.CommandWrapper
import AccountKeyBehavior._
import akka.actor.typed.ActorSystem
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import app.softnetwork.session.service.BasicSessionMaterials
import com.softwaremill.session.RefreshTokenStorage
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

/** Created by smanciot on 19/04/2020.
  */
class AccountKeyBehaviorSpec
    extends AnyWordSpecLike
    with BasicAccountTestKit
    with BasicSessionMaterials[Session] {

  override implicit def ts: ActorSystem[_] = tsystem

  override implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(
    ts
  )

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  val kvProbe: TestProbe[KvCommandResult] = createTestProbe[KvCommandResult]()

  "AccountKey" must {
    "add key" in {
      val ref = entityRefFor(TypeKey, "add")
      ref ! CommandWrapper(Put("account"), kvProbe.ref)
      kvProbe.expectMessageType[KvAdded.type]
    }

    "remove key" in {
      val ref = entityRefFor(TypeKey, "remove")
      ref ! Put("account")
      ref ! CommandWrapper(Remove, kvProbe.ref)
      kvProbe.expectMessageType[KvRemoved.type]
    }

    "lookup key" in {
      val ref = entityRefFor(TypeKey, "lookup")
      ref ! Put("account")
      ref ! CommandWrapper(Lookup, kvProbe.ref)
      kvProbe.expectMessage(KvFound("account"))
      val ref2 = entityRefFor(TypeKey, "empty")
      ref2 ! CommandWrapper(Lookup, kvProbe.ref)
      kvProbe.expectMessageType[KvNotFound.type]
    }
  }
}
