package app.softnetwork.account.api

import app.softnetwork.persistence.schema.SchemaProvider

trait BasicAccountEndpointsApi extends BasicAccountApi with BasicAccountEndpoints {
  _: SchemaProvider =>
}
