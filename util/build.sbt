name := "elementary.util"

version := "1.0"

assemblyJarName in assembly := "util.jar"
assemblyJarName in assemblyPackageDependency := "util-deps.jar"

libraryDependencies +=  "org.scalaj" %% "scalaj-http" % "1.1.4"
