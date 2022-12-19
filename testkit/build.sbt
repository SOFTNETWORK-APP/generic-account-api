import app.softnetwork.sbt.build.Versions

Test / parallelExecution := false

organization := "app.softnetwork.account"

name := "account-testkit"

libraryDependencies ++= Seq(
  "app.softnetwork.notification" %% "notification-testkit" % Versions.notification
//  "app.softnetwork.scheduler" %% "scheduler-testkit" % Versions.scheduler,
//  "app.softnetwork.persistence" %% "persistence-session-testkit" % Versions.genericPersistence
)
