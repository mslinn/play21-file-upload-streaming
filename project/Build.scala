import sbt._
import sbt.Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "play21-file-upload-streaming"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "org.scalaz" %% "scalaz-core" % "7.0.0",
    "nl.rhinofly" %% "api-s3" % "1.7.2",
    "com.github.nscala-time" %% "nscala-time" % "0.4.0"
  )

  scalaVersion := "2.10.1"

 scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-target:jvm-1.6", "-unchecked",
    "-Ywarn-adapted-args", "-Ywarn-value-discard", "-Xlint")

  val main = play.Project(appName, appVersion, appDependencies).settings(
    // Add your own project settings here
    resolvers += Resolver.url("Markus Jura github repo play-libraries", url("http://markusjura.github.com/play-libraries"))(Resolver.ivyStylePatterns),
    resolvers += "Rhinofly Internal Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local"
  )

}
