package app.softnetwork.account.persistence.typed

import app.softnetwork.account.handlers.MockGenerator

object MockBasicAccountBehavior extends BasicAccountBehavior with MockGenerator {
  override def persistenceId: String = "MockAccount"
}
