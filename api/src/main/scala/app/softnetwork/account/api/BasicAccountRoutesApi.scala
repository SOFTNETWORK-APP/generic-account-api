package app.softnetwork.account.api

import app.softnetwork.persistence.schema.SchemaProvider

trait BasicAccountRoutesApi extends BasicAccountApi with BasicAccountRoutes { _: SchemaProvider => }
