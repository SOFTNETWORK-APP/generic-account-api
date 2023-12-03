package app.softnetwork.account.persistence.typed

import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.persistence.message.CommandWrapper
import app.softnetwork.account.message._
import MockBasicAccountBehavior._
import akka.actor.typed.ActorSystem
import app.softnetwork.account.scalatest.BasicAccountTestKit
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import app.softnetwork.session.service.BasicSessionMaterials
import com.softwaremill.session.RefreshTokenStorage
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

/** Created by smanciot on 19/04/2020.
  */
class BasicAccountBehaviorSpec
    extends AnyWordSpecLike
    with BasicAccountTestKit
    with BasicSessionMaterials[Session] {

  override implicit def ts: ActorSystem[_] = tsystem

  override implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(
    ts
  )

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  private val username = "smanciot"

  private val password = "Changeit1"

  "SignUp" must {
    "fail if confirmed password does not match password" in {
      val probe = createTestProbe[AccountCommandResult]()
      val ref = entityRefFor(TypeKey, "PasswordsNotMatched")
      ref ! CommandWrapper(BasicAccountSignUp(username, password, Some("fake")), probe.ref)
      probe.expectMessage(PasswordsNotMatched)
    }
  }
}
