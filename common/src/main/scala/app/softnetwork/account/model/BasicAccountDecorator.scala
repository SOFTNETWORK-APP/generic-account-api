package app.softnetwork.account.model

trait BasicAccountDecorator { _: BasicAccount =>
  override def newProfile(name: String): Profile =
    BasicAccountProfile.defaultInstance.withName(name)
}
