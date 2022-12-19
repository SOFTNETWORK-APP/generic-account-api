package app.softnetwork.account.persistence.typed

import app.softnetwork.kv.persistence.typed.KeyValueBehavior
import app.softnetwork.account.config.Settings

trait AccountKeyBehavior extends KeyValueBehavior {

  override def persistenceId: String = "AccountKeys"

  /** @return
    *   node role required to start this actor
    */
  override lazy val role: String = Settings.AkkaNodeRole
}

object AccountKeyBehavior extends AccountKeyBehavior
