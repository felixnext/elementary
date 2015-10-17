import Dependencies._
import org.scalastyle.sbt.ScalastylePlugin

name := """elementary"""
lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

// basic settings for every sub-project
lazy val commonSettings = ScalastylePlugin.settings ++ Seq(
  organization := "ifis.cs.tu-bs.de",
  scalaVersion := "2.11.6",
  // add stuff for scalastyle
  compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Compile).toTask("").value,
  (compile in Compile) <<= (compile in Compile) dependsOn compileScalastyle,
  scalastyleConfig := (baseDirectory.value / ".." / "scalastyle-config.xml"),
  // do not use tests during assembly
  test in assembly := {},
  // Sets the assembly task to use external Dependency file (create with assemblyPackageDependency task)
  assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false, includeDependency = false),
  assemblyOutputPath in assembly := (baseDirectory.value / ".." / "deploy" / (assemblyJarName in assembly).value),
  assemblyOutputPath in assemblyPackageDependency := (baseDirectory.value / ".." / "deploy" / (assemblyJarName in assemblyPackageDependency).value),
  // merge Strategy to avoid errors in the assembly
  assemblyMergeStrategy in assembly := {
    //case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
    case "application.conf"                            => MergeStrategy.concat
    //case "unwanted.txt"                                => MergeStrategy.discard
    // default config (from Assembly)
    case x if Assembly.isConfigFile(x) =>
      MergeStrategy.concat
    case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
      MergeStrategy.rename
    case PathList("META-INF", xs @ _*) =>
      (xs map {_.toLowerCase}) match {
        case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
          MergeStrategy.discard
        case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
          MergeStrategy.discard
        case "plexus" :: xs =>
          MergeStrategy.discard
        case "services" :: xs =>
          MergeStrategy.filterDistinctLines
        case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
          MergeStrategy.filterDistinctLines
        case ("io.netty.versions.properties" :: Nil) | ("spring.tooling" :: Nil) => MergeStrategy.last
        case _ => MergeStrategy.deduplicate
      }
    case _ => MergeStrategy.last // NOTE: only until Akka 2.4
  //  case x => MergeStrategy.first
  //    val oldStrategy = (assemblyMergeStrategy in assembly).value
  //    oldStrategy(x)
  }
)

// add scalastyle task to the compile task
//lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")
//compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Compile).toTask("").value
//(compile in Compile) <<= (compile in Compile) dependsOn compileScalastyle

// options for the scala compiler
scalacOptions in Global ++= Seq("-unchecked", "-deprecation", "-feature")

// disable parallel execution for the tests
parallelExecution in Global := false

resolvers in Global ++= Seq(
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases",
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Sonatype Repository" at "https://oss.sonatype.org/content/repositories/snapshots",
  Resolver.bintrayRepo("mfglabs", "maven")
)

// defines some basic tools that can be used troughout the project
lazy val util = (project in file("util"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, buildInfoBuildNumber),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoOptions += BuildInfoOption.ToJson,
    buildInfoPackage := "util.build"
  )
  .settings(commonSettings: _*)
  .settings(
    Seq(
      libraryDependencies ++= basicDeps,
      libraryDependencies ++= akkaDeps ++ sprayJson,
      libraryDependencies ++= mongoDeps ++ elasticDeps ++ virtuosoDeps,
      libraryDependencies ++= breezeEpicDeps ++ mlDeps
    ): _*
  )

// project for interop with python
lazy val glue = (project in file("glue"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, buildInfoBuildNumber),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoOptions += BuildInfoOption.ToJson,
    buildInfoPackage := "glue.build"
  )
  .settings(commonSettings: _*)
  .settings(
    Seq(
      libraryDependencies ++= basicDeps,
      libraryDependencies ++= akkaDeps
    ): _*
  )
  .dependsOn(util)

// project for interop with python
lazy val ml = (project in file("machinelearning"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, buildInfoBuildNumber),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoOptions += BuildInfoOption.ToJson,
    buildInfoPackage := "machinelearning.build"
  )
  .settings(commonSettings: _*)
  .settings(
    Seq(
      libraryDependencies ++= basicDeps,
      libraryDependencies ++= akkaDeps
    ): _*
  )
  .dependsOn(util, glue)

// defines a tool to analyze the corpus information
lazy val corpus_analyzer = (project in file("tools/corpus_analyzer"))
  .settings(commonSettings: _*)
  .settings(
    Seq(
      libraryDependencies ++= basicDeps ++ akkaDeps ++ akkaStreamDeps,
      libraryDependencies ++= elasticDeps ++ slickDeps,
      libraryDependencies ++= breezeEpicDeps
    ): _*
  )
  .dependsOn(util, ml)

// defines a tool to add base corpus data to ElasticSearch
lazy val base_importer = (project in file("tools/base_importer"))
  .settings(commonSettings: _*)
  .settings(
    Seq(
      libraryDependencies ++= basicDeps ++ xmlDeps,
      libraryDependencies ++= elasticDeps,
      libraryDependencies ++= deep4j
    ): _*
  )
  .dependsOn(util, ml)

// defines a tool to generate an entity index
lazy val ontology_indexer = (project in file("tools/ontology_indexer"))
  .settings(commonSettings: _*)
  .settings(
    Seq(
      libraryDependencies ++= basicDeps,
      libraryDependencies ++= elasticDeps
    ): _*
  )
  .dependsOn(util, ml)

// defines a tool to crawl questions from yahoo answers
lazy val answers_crawler = (project in file("tools/answers_crawler"))
  .settings(commonSettings: _*)
  .settings(
    Seq(
      libraryDependencies ++= basicDeps ++ akkaDeps ++ akkaStreamDeps,
      libraryDependencies ++= scraperDeps,
      libraryDependencies ++= mongoDeps
    ): _*
  )
  .dependsOn(util)

// defines a tool to analyze questions crawled from yahoo answers
lazy val question_analysis = (project in file("tools/question_analysis"))
  .settings(commonSettings: _*)
  .settings(
    Seq(
      libraryDependencies ++= basicDeps ++ akkaDeps ++ akkaStreamDeps,
      libraryDependencies ++= breezeDeps ++ epicDeps, //++ sistanlpDeps
      libraryDependencies ++= tepkinDeps
    ): _*
  )
  .dependsOn(util)

//lazy val frontend = (project in file("frontend"))

// statistic framework
lazy val statistics = (project in file("statistic-framework"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, buildInfoBuildNumber),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoOptions += BuildInfoOption.ToJson,
    buildInfoPackage := "statistics.build"
  )
  .settings(commonSettings: _*)
  .settings(
    Seq(
      libraryDependencies ++= basicDeps,
      libraryDependencies ++= akkaStreamDeps ++ akkaDeps ++ akkaHttpDeps ++ akkaRemoteDeps,
      //libraryDependencies ++= kamonDeps,
      libraryDependencies ++= breezeEpicDeps,
      libraryDependencies ++= mongoDeps ++ slickDeps
    ): _*
  )
  .dependsOn(glue, util)

// crawlers to extract and enrich information from transcripts and web data (also build indexs)
lazy val crawlers = (project in file("crawlers"))
  .settings(commonSettings: _*)
  .settings(
    Seq(
      libraryDependencies ++= basicDeps,
      libraryDependencies ++= akkaHttpDeps ++ akkaStreamDeps,
      libraryDependencies ++= breezeEpicDeps
    ): _*
  )

// akka http front-end api for the system
lazy val questionapi = (project in file("question-api"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, buildInfoBuildNumber),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoOptions += BuildInfoOption.ToJson,
    buildInfoPackage := "api.build"
  )
  .settings(commonSettings: _*)
  .settings(
    Seq(
      libraryDependencies ++= basicDeps,
      //libraryDependencies ++= kamonDeps,
      libraryDependencies ++= akkaDeps ++ akkaHttpDeps ++ akkaRemoteDeps
    ): _*
  )
  .dependsOn(util)

// main processing pipeline of the system (distributed via akka cluster)
lazy val pipeline = (project in file("pipeline"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, buildInfoBuildNumber),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoOptions += BuildInfoOption.ToJson,
    buildInfoPackage := "pipeline.build"
  )
  .settings(commonSettings: _*)
  .settings(
    Seq(
      libraryDependencies ++= basicDeps,
      libraryDependencies ++= akkaDeps ++ akkaStreamDeps ++ akkaRemoteDeps,
      //libraryDependencies ++= kamonDeps,
      libraryDependencies ++= breezeEpicDeps,
      libraryDependencies ++= elasticDeps ++ virtuosoDeps
    ): _*
  )
  .dependsOn(glue, util, ml)

//
lazy val clients = (project in file("clients"))
.settings(commonSettings: _*)
.settings(
  Seq(
    libraryDependencies ++= basicDeps,
    libraryDependencies ++= akkaHttpDeps
  ): _*
)

lazy val deployment = (project in file("."))
   .aggregate(util, glue, ml, statistics, questionapi, pipeline)
