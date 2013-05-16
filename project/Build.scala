import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {
  val appName         = "play21-file-upload-streaming"
  val appVersion      = "1.0-SNAPSHOT"

  scalaVersion := "2.10.1"

  val appDependencies = Seq(
    "com.amazonaws" % "aws-java-sdk" % "1.4.2.1"  withSources()
  )

  val main = play.Project(appName, appVersion, appDependencies)
}
