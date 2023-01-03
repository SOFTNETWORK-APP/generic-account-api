import app.softnetwork.sbt.build._

organization := "app.softnetwork.account"

name := "account-core"

libraryDependencies ++= Seq(
  "app.softnetwork.session" %% "session-core" % Versions.session,
  "app.softnetwork.scheduler" %% "scheduler-core" % Versions.scheduler
)

