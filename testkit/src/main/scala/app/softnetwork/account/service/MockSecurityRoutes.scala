package app.softnetwork.account.service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.ApiRoutes

trait MockSecurityRoutes extends ApiRoutes {

  override def apiRoutes(system: ActorSystem[_]): Route = MockBasicAccountService(system).route

}
