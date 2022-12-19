package app.softnetwork.account.api

import app.softnetwork.persistence.jdbc.query.PostgresSchemaProvider

object BasicAccountPostgresLauncher extends BasicAccountApi with PostgresSchemaProvider
