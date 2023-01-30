package app.softnetwork.account.model

import app.softnetwork.security.Sha512Encryption
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import app.softnetwork.account.message.BasicAccountSignUp
import Sha512Encryption._

/** Created by smanciot on 18/03/2018.
  */
class BasicAccountSpec extends AnyWordSpec with Matchers {

  private val username = "smanciot"
  private val email = "stephane.manciot@gmail.com"
  private val gsm = "33660010203"
  private val password = "changeit"

  private val profile =
    BasicAccountProfile.defaultInstance
      .withName("name")
      .withType(ProfileType.CUSTOMER)
      .withEmail(email)
      .withPhoneNumber(gsm)
      .withUserName(username)

  "BasicAccount creation" should {
    "work with username" in {
      BasicAccount(BasicAccountSignUp(username, password, profile = Some(profile))) match {
        case Some(basicAccount) =>
          assert(basicAccount.username.getOrElse("") == username)
          assert(checkEncryption(basicAccount.credentials, password))
          basicAccount.profile(Some(profile.name)) match {
            case Some(p) =>
              assert(p.`type` == profile.`type`)
              assert(p.email == profile.email)
              assert(p.phoneNumber == profile.phoneNumber)
              assert(p.userName == profile.userName)
            case _ => fail()
          }
        case _ => fail()
      }
    }
    "work with email" in {
      BasicAccount(BasicAccountSignUp(email, password, profile = Some(profile))) match {
        case Some(basicAccount) =>
          assert(basicAccount.email.getOrElse("") == email)
          assert(checkEncryption(basicAccount.credentials, password))
          basicAccount.profile(Some(profile.name)) match {
            case Some(p) =>
              assert(p.`type` == profile.`type`)
              assert(p.email == profile.email)
              assert(p.phoneNumber == profile.phoneNumber)
              assert(p.userName == profile.userName)
            case _ => fail()
          }
        case _ => fail()
      }
    }
    "work with gsm" in {
      BasicAccount(BasicAccountSignUp(gsm, password, profile = Some(profile))) match {
        case Some(basicAccount) =>
          assert(basicAccount.gsm.getOrElse("") == gsm)
          assert(checkEncryption(basicAccount.credentials, password))
          basicAccount.profile(Some(profile.name)) match {
            case Some(p) =>
              assert(p.`type` == profile.`type`)
              assert(p.email == profile.email)
              assert(p.phoneNumber == profile.phoneNumber)
              assert(p.userName == profile.userName)
            case _ => fail()
          }
        case _ => fail()
      }
    }
  }

}
