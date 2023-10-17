Test / parallelExecution := false

organization := "app.softnetwork.account"

name := "account-testkit"

libraryDependencies ++= Seq(
  "app.softnetwork.notification" %% "notification-testkit" % Versions.notification,
  "org.scalatest" %% "scalatest" % Versions.scalatest
)
