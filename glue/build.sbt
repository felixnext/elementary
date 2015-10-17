name := """elementary.glue"""

version := "1.0"

assemblyJarName in assembly := "glue.jar"
assemblyJarName in assemblyPackageDependency := "glue-deps.jar"

resolvers ++= Seq(
  "sonatype repository" at "https://oss.sonatype.org/content/repositories/snapshots",
  "sonatype" at "http://oss.sonatype.org/content/groups/public"
)

libraryDependencies ++= Seq(
  "com.nativelibs4java" % "bridj" % "0.6.2"
)
