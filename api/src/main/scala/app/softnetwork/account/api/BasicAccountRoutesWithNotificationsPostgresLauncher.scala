package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.api.AllNotificationsApi
import app.softnetwork.persistence.jdbc.schema.{JdbcSchemaProvider, JdbcSchemaTypes}
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.persistence.query.EventProcessorStream
import app.softnetwork.persistence.schema.SchemaType
import app.softnetwork.session.CsrfCheckHeader
import app.softnetwork.session.api.SessionApi
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

object BasicAccountRoutesWithNotificationsPostgresLauncher
    extends AllNotificationsApi[Session]
    with BasicAccountRoutesApi[Session]
    with SessionApi
    with JdbcSchemaProvider
    with CsrfCheckHeader {

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override def schemaType: SchemaType = JdbcSchemaTypes.Postgres

  /** initialize all entities
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    super.entities(sys) ++ notificationEntities(sys)

  /** initialize all event processor streams
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    super.eventProcessorStreams(sys) ++ notificationEventProcessorStreams(sys)

}
