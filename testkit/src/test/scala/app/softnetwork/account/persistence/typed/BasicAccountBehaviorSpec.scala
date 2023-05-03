package app.softnetwork.account.persistence.typed

import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.persistence.message.CommandWrapper
import app.softnetwork.account.message._
import MockBasicAccountBehavior._
import app.softnetwork.account.scalatest.BasicAccountTestKit
import org.slf4j.{Logger, LoggerFactory}

/** Created by smanciot on 19/04/2020.
  */
class BasicAccountBehaviorSpec extends AnyWordSpecLike with BasicAccountTestKit {

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
