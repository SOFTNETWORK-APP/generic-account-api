Test / parallelExecution := false

organization := "app.softnetwork.account"

name := "account-testkit"

val jacksonExclusions = Seq(
  ExclusionRule(organization = "com.fasterxml.jackson.core"),
  ExclusionRule(organization = "com.fasterxml.jackson.databind"),
  ExclusionRule(organization = "com.fasterxml.jackson.jaxrs"),
  ExclusionRule(organization = "com.fasterxml.jackson.module.scala"),
  ExclusionRule(organization = "com.fasterxml.jackson.module")
)

libraryDependencies ++= Seq(
  "app.softnetwork.notification" %% "notification-testkit" % Versions.notification,
  "com.github.scribejava" % "scribejava-core" % Versions.scribe classifier "tests" excludeAll (jacksonExclusions *),
  "org.scalatest" %% "scalatest" % Versions.scalatest
)
