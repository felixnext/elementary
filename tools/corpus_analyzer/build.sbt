name := "elementary.tools.corpus_analyzer"

version := "1.0"

assemblyJarName in assembly := "corpus_analyzer.jar"
assemblyJarName in assemblyPackageDependency := "corpus_analyzer-deps.jar"

libraryDependencies += "io.spray" %%  "spray-json" % "1.3.2"
//libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.1.2"
