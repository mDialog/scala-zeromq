
name := "scala-zeromq"

organization := "com.mdialog"

version := "1.1.1"

scalaVersion := "2.11.0"

crossScalaVersions := Seq("2.10.4", "2.11.0")

parallelExecution := false

scalacOptions ++= Seq("-unchecked", "-deprecation", "-language:postfixOps")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.2",
  "org.scalatest" %% "scalatest" % "2.1.3" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test"
)

resolvers ++= Seq(
 //   "Sonatype (releases)" at "https://oss.sonatype.org/content/repositories/releases/",
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

publishTo <<= version { (v: String) =>
  if (v.trim.endsWith("-SNAPSHOT"))
    Some(Resolver.file("Snapshots", file("../mdialog.github.com/snapshots/")))
  else
    Some(Resolver.file("Releases", file("../mdialog.github.com/releases/")))
}
