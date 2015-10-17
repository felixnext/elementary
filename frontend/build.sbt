name := """elementary.frontend"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws,
  "com.adrianhurt"          %% "play-bootstrap3"    % "0.4.2",
  "com.sksamuel.elastic4s"  %% "elastic4s"          % "1.5.10"
)


fork in run := true