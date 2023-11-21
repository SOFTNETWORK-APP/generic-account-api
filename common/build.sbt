organization := "app.softnetwork.account"

name := "account-common"

val jacksonExclusions = Seq(
  ExclusionRule(organization = "com.fasterxml.jackson.core"),
  ExclusionRule(organization = "com.fasterxml.jackson.databind"),
  ExclusionRule(organization = "com.fasterxml.jackson.jaxrs"),
  ExclusionRule(organization = "com.fasterxml.jackson.module.scala"),
  ExclusionRule(organization = "com.fasterxml.jackson.module")
)

libraryDependencies ++= Seq(
  "org.passay" % "passay" % "1.3.1",
  "com.github.scribejava" % "scribejava-apis" % Versions.scribe excludeAll (jacksonExclusions *),
  "app.softnetwork.notification" %% "notification-common" % Versions.notification,
  "app.softnetwork.notification" %% "notification-common" % Versions.notification % "protobuf",
  "app.softnetwork.api" %% "generic-server-api" % Versions.genericPersistence,
  "app.softnetwork.protobuf" %% "scalapb-extensions" % "0.1.7"
)

Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/protobuf"

Test / parallelExecution := false
