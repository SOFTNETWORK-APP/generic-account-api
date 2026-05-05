package app.softnetwork.account.model

import app.softnetwork.persistence.generateUUID

import java.time.Instant

trait BasicAccountProfileCompanion {

  def apply(
    name: String,
    `type`: ProfileType,
    firstName: String,
    lastName: String
  ): BasicAccountProfile = {
    val now = Instant.now()
    BasicAccountProfile.defaultInstance
      .withUuid(generateUUID())
      .withCreatedDate(now)
      .withLastUpdated(now)
      .withName(name)
      .withType(`type`)
      .withFirstName(firstName)
      .withLastName(lastName)
  }
}
