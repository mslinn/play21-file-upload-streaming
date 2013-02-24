import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {
  val appName         = "play21-file-upload-streaming"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "com.amazonaws" % "aws-java-sdk" % "1.3.32"  withSources()
  )

  val main = play.Project(appName, appVersion, appDependencies)
}
