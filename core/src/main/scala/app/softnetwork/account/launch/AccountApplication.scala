package app.softnetwork.account.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.launch.Application
import app.softnetwork.notification.model.Notification
import app.softnetwork.persistence.query.SchemaProvider
import app.softnetwork.account.config.AccountSettings
import app.softnetwork.account.handlers.AccountDao
import app.softnetwork.account.model.{Account, AccountDecorator, Profile, ProfileDecorator}

/** Created by smanciot on 22/03/2018.
  */
trait AccountApplication[
  T <: Account with AccountDecorator,
  P <: Profile with ProfileDecorator,
  N <: Notification
] extends Application
    with AccountRoutes[T, P, N] {
  self: SchemaProvider =>

  def accountDao: AccountDao

  def initAuthSystem: ActorSystem[_] => Unit = system => {
    val root = AccountSettings.AdministratorsConfig.root
    accountDao.initAdminAccount(root.login, root.password)(system)
  }

  override def initSystem: ActorSystem[_] => Unit = system => {
    initAuthSystem(system)
    initSchedulerSystem(system)
  }
}
