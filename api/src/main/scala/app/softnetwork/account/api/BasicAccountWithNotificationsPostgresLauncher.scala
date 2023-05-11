package app.softnetwork.account.api

import akka.actor
import akka.actor.typed.ActorSystem
import app.softnetwork.notification.api.AllNotificationsApi
import app.softnetwork.persistence.jdbc.schema.{JdbcSchema, JdbcSchemaTypes}
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.persistence.query.EventProcessorStream
import app.softnetwork.persistence.schema.{Schema, SchemaProvider, SchemaType}
import app.softnetwork.persistence.typed._
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}

object BasicAccountWithNotificationsPostgresLauncher
    extends AllNotificationsApi
    with BasicAccountApi
    with SchemaProvider {

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override def schema: ActorSystem[_] => Schema = sys =>
    new JdbcSchema {
      override def schemaType: SchemaType = JdbcSchemaTypes.Postgres
      override implicit def classicSystem: actor.ActorSystem = sys
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
