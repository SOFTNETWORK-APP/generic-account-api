package app.softnetwork.account.api

import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck

trait BasicAccountRoutesApi extends BasicAccountApi with BasicAccountRoutes {
  _: SchemaProvider with CsrfCheck =>
}
