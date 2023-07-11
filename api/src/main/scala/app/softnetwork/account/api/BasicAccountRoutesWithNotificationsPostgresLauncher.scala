package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.api.AllNotificationsApi
import app.softnetwork.persistence.jdbc.schema.{JdbcSchemaProvider, JdbcSchemaTypes}
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.persistence.query.EventProcessorStream
import app.softnetwork.persistence.schema.SchemaType
import app.softnetwork.session.service.SessionService
import org.slf4j.{Logger, LoggerFactory}

object BasicAccountRoutesWithNotificationsPostgresLauncher
    extends AllNotificationsApi
    with BasicAccountRoutesApi
    with JdbcSchemaProvider {

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override def schemaType: SchemaType = JdbcSchemaTypes.Postgres

  override def sessionService: ActorSystem[_] => SessionService = system =>
    SessionService.oneOffCookie(system)

  /** initialize all entities
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    super.entities(sys) ++ notificationEntities(sys)

  /** initialize all event processor streams
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    super.eventProcessorStreams(sys) ++ notificationEventProcessorStreams(sys)
}