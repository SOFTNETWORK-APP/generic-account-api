organization := "app.softnetwork.account"

name := "account-core"

libraryDependencies ++= Seq(
  "app.softnetwork.session" %% "session-core" % Versions.genericPersistence
)

