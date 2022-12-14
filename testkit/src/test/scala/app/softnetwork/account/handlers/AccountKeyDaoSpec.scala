package app.softnetwork.account.handlers

import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.account.scalatest.BasicAccountTestKit

import AccountKeyDao._

/** Created by smanciot on 19/04/2020.
  */
class AccountKeyDaoSpec extends AnyWordSpecLike with BasicAccountTestKit {

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
