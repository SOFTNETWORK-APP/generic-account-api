package app.softnetwork.account.service

import akka.actor.typed.ActorSystem
import app.softnetwork.account.handlers.MockBasicAccountTypeKey

object MockBasicAccountService {
  def apply(asystem: ActorSystem[_]): MockBasicAccountService = {
    new MockBasicAccountService {
      override implicit def system: ActorSystem[_] = asystem
    }
  }
}

trait MockBasicAccountService extends BasicAccountService with MockBasicAccountTypeKey
