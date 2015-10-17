import sbt._

object Dependencies {
  // Versions
  lazy val akkaVersion    = "2.3.12"
  lazy val akkaSVersion   = "1.0"
  lazy val kamonVersion   = "0.4.0"
  lazy val breezeVersion  = "0.11.2"
  lazy val sparkVersion   = "1.4.0"
  lazy val deep4jVersion  = "0.0.3.3.4.alpha2"

  // Libraries
  val scalaTest       = "org.scalatest"           %% "scalatest"      % "2.2.4" % "test"
  val scalaZ          = "org.scalaz"              %% "scalaz-core"    % "7.1.2"
  val sparkCore       = "org.apache.spark"        %% "spark-core"     % sparkVersion
  val sparkStream     = "org.apache.spark"        %% "spark-streaming"% sparkVersion
  val sparkSql        = "org.apache.spark"        %% "spark-sql"      % sparkVersion
  val akkaActor       = "com.typesafe.akka"       %% "akka-actor"     % akkaVersion
  val akkaAgent       = "com.typesafe.akka"       %% "akka-agent"     % akkaVersion
  val akkaTestkit     = "com.typesafe.akka"       %% "akka-testkit"   % akkaVersion
  val akkaCluster     = "com.typesafe.akka"       %% "akka-cluster"   % akkaVersion
  val akkaRemote      = "com.typesafe.akka"       %% "akka-remote"    % akkaVersion
  val elasticStream   = "com.mfglabs"             %% "akka-stream-extensions-elasticsearch"   % "0.7.1"
  val akkaStreams     = "com.typesafe.akka"       %% "akka-stream-experimental"           % akkaSVersion
  val akkaStreamTest  = "com.typesafe.akka"       %% "akka-stream-testkit-experimental"   % akkaSVersion
  val akkaHttp        = "com.typesafe.akka"       %% "akka-http-core-experimental"        % akkaSVersion
  val akkaHttpSpray   = "com.typesafe.akka"       %% "akka-http-spray-json-experimental"  % akkaSVersion
  val akkaHttpScala   = "com.typesafe.akka"       %% "akka-http-experimental"             % akkaSVersion
  val akkaHttpTest    = "com.typesafe.akka"       %% "akka-http-testkit-experimental"     % akkaSVersion
  val breeze          = "org.scalanlp"            %% "breeze"         % breezeVersion
  val breezeNative    = "org.scalanlp"            %% "breeze-natives" % breezeVersion
  val breezeViz       = "org.scalanlp"            %% "breeze-viz"     % breezeVersion
  val epicEn          = "org.scalanlp"            %% "english"        % "2015.1.25"
  val kamonCore       = "io.kamon"                %% "kamon-core"           % kamonVersion
  val kamonReport     = "io.kamon"                %% "kamon-log-reporter"   % kamonVersion
  val kamonStats      = "io.kamon"                %% "kamon-statsd"         % kamonVersion
  val kamonMetrics    = "io.kamon"                %% "kamon-system-metrics" % kamonVersion
  val kamonAspect     = "org.aspectj"             %  "aspectjweaver"        % "1.8.1"
  val mongoCasbah     = "org.mongodb"             %% "casbah"         % "2.8.1"
  val mongoReact      = "org.reactivemongo"       %% "reactivemongo"  % "0.10.5.0.akka23"
  val mongoTepkin     = "net.fehmicansaglam"      %% "tepkin"         % "0.5"
  val slick           = "com.typesafe.slick"      %% "slick"          % "3.0.0"
  val mysql           = "mysql"                   %  "mysql-connector-java" % "5.1.27"
  val elastic         = "com.sksamuel.elastic4s"  %% "elastic4s-core"       % "1.5.17"
  val log4j           = "org.slf4j"               %  "log4j-over-slf4j"     % "1.7.7"
  val jenaCore        = "org.apache.jena"         %  "jena-core"      % "2.13.0"
  val jenaArq         = "org.apache.jena"         %  "jena-arq"       % "2.13.0"
  val scalaXML        = "org.scala-lang.modules"  %% "scala-xml"      % "1.0.2"
  val mlMetrics       = "com.rockymadden.stringmetric" %% "stringmetric-core" % "0.27.4"
  val scalaScraper    = "net.ruippeixotog"        %% "scala-scraper"  % "0.1.1"
  val sistanlpProc    = "edu.arizona.sista"       %% "processors"     % "5.3"
  val sistanlpModels  = "edu.arizona.sista"       %% "processors"     % "5.3" classifier "models"
  val deep4jUI        = "org.deeplearning4j"      %  "deeplearning4j-ui"    % deep4jVersion
  val deep4jNLP       = "org.deeplearning4j"      %  "deeplearning4j-nlp"   % deep4jVersion exclude("com.typesafe.akka", "akka-actor_2.10") exclude("com.typesafe.akka", "akka-remote_2.10")
  val deep4jCore      = "org.deeplearning4j"      %  "deeplearning4j-core"  % deep4jVersion exclude("com.typesafe.akka", "akka-remote_2.10")


  // Library deps
  val basicDeps       = Seq(scalaTest, scalaZ)
  val kamonDeps       = Seq(kamonCore, kamonStats, kamonReport, kamonAspect)
  val sparkDeps       = Seq(sparkCore, sparkStream, sparkSql)
  val akkaDeps        = Seq(akkaActor, akkaAgent, akkaTestkit)
  val akkaClusterDeps = Seq(akkaCluster)
  val akkaStreamDeps  = Seq(akkaStreams, akkaStreamTest, elasticStream)
  val akkaHttpDeps    = Seq(akkaHttp, akkaHttpScala, akkaHttpSpray, akkaHttpTest)
  val sprayJson       = Seq(akkaHttpSpray)
  val akkaRemoteDeps  = Seq(akkaRemote)
  val breezeDeps      = Seq(breeze, breezeNative, breezeViz)
  val epicDeps        = Seq(epicEn)
  val breezeEpicDeps  = breezeDeps ++ epicDeps
  val mongoDeps       = Seq(mongoReact)
  val slickDeps       = Seq(slick, mysql)
  val elasticDeps     = Seq(elastic)
  val virtuosoDeps    = Seq(jenaCore, jenaArq)
  val xmlDeps         = Seq(scalaXML)
  val mlDeps          = Seq(mlMetrics)
  val tepkinDeps      = Seq(mongoTepkin)
  val scraperDeps     = Seq(scalaScraper)
  val sistanlpDeps    = Seq(sistanlpProc, sistanlpModels)
  val deep4j          = Seq(deep4jCore, deep4jNLP)
}
