package app.softnetwork.account.api

import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}

trait BasicAccountEndpointsApi[SD <: SessionData with SessionDataDecorator[SD]]
    extends BasicAccountApi[SD]
    with BasicAccountEndpoints[SD] {
  _: SchemaProvider with CsrfCheck =>
}
