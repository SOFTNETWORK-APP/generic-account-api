package app.softnetwork.account.api

import akka.{actor => classic}
import akka.actor.typed.ActorSystem
import app.softnetwork.notification.api.AllNotificationsApi
import app.softnetwork.persistence.jdbc.schema.PostgresSchemaProvider
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.persistence.query.EventProcessorStream
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.persistence.typed._
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}

object BasicAccountWithNotificationsPostgresLauncher
    extends AllNotificationsApi
    with BasicAccountApi {

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override def schemaProvider: ActorSystem[_] => SchemaProvider = sys =>
    new PostgresSchemaProvider {
      override implicit def classicSystem: classic.ActorSystem = sys

      override def config: Config = BasicAccountWithNotificationsPostgresLauncher.this.config
    }

  /** initialize all entities
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    super.entities(sys) ++ notificationEntities(sys)

  /** initialize all event processor streams
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    super.eventProcessorStreams(sys) ++ notificationEventProcessorStreams(sys)
}
