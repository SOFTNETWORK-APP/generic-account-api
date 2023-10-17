organization := "app.softnetwork.account"

name := "account-common"

libraryDependencies ++= Seq(
  "org.passay" % "passay" % "1.3.1",
  "app.softnetwork.notification" %% "notification-common" % Versions.notification,
  "app.softnetwork.notification" %% "notification-common" % Versions.notification % "protobuf",
  "app.softnetwork.api" %% "generic-server-api" % Versions.genericPersistence,
  "app.softnetwork.protobuf" %% "scalapb-extensions" % "0.1.7"
)

Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/protobuf"

Test / parallelExecution := false
