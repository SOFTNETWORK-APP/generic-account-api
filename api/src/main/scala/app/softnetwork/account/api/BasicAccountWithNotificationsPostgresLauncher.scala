package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.api.AllNotificationsApi
import app.softnetwork.persistence.jdbc.query.PostgresSchemaProvider
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.persistence.query.EventProcessorStream

object BasicAccountWithNotificationsPostgresLauncher
    extends AllNotificationsApi
    with BasicAccountApi
    with PostgresSchemaProvider {

  /** initialize all entities
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    super.entities(sys) ++ notificationEntities(sys)

  /** initialize all event processor streams
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    super.eventProcessorStreams(sys) ++ notificationEventProcessorStreams(sys)
}
