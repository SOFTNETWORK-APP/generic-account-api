package app.softnetwork.account.api

import app.softnetwork.persistence.jdbc.schema.JdbcSchemaTypes
import app.softnetwork.persistence.schema.SchemaType
import org.slf4j.{Logger, LoggerFactory}

object BasicAccountPostgresLauncher extends BasicAccountApi {
  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override val schemaType: SchemaType = JdbcSchemaTypes.Postgres
}
