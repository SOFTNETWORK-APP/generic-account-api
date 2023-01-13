package app.softnetwork.account.handlers

import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.account.message.AccountCommand
import app.softnetwork.account.persistence.typed.MockBasicAccountBehavior
import app.softnetwork.persistence.typed.CommandTypeKey

import scala.reflect.ClassTag

trait MockBasicAccountTypeKey extends CommandTypeKey[AccountCommand] {
  override def TypeKey(implicit tTag: ClassTag[AccountCommand]): EntityTypeKey[AccountCommand] =
    MockBasicAccountBehavior.TypeKey
}
