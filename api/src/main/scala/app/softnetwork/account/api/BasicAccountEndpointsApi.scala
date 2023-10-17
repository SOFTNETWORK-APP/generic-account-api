package app.softnetwork.account.api

import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck

trait BasicAccountEndpointsApi extends BasicAccountApi with BasicAccountEndpoints {
  _: SchemaProvider with CsrfCheck =>
}
