package app.softnetwork.account.api

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.jdbc.schema.{JdbcSchemaProvider, JdbcSchemaTypes}
import app.softnetwork.persistence.schema.SchemaType
import app.softnetwork.session.CsrfCheckHeader
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import app.softnetwork.session.model.SessionDataCompanion
import com.softwaremill.session.RefreshTokenStorage
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

object BasicAccountEndpointsPostgresLauncher
    extends BasicAccountEndpointsApi[Session]
    with JdbcSchemaProvider
    with CsrfCheckHeader {
  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override def schemaType: SchemaType = JdbcSchemaTypes.Postgres

  override implicit def companion: SessionDataCompanion[Session] = Session

  override protected def refreshTokenStorage: ActorSystem[_] => RefreshTokenStorage[Session] =
    sys => SessionRefreshTokenDao(sys)
}
