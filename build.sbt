
name := "scala-zeromq"

organization := "com.mdialog"

version := "1.1.1"

scalaVersion := "2.11.7"

parallelExecution := false

scalacOptions ++= Seq("-unchecked", "-deprecation", "-language:postfixOps")

// @varargs behavior changed between JDK7/8, so only run java tests on JDK8 or above
unmanagedSourceDirectories in Test := {
  if (sys.props("java.specification.version") < "1.8")
    (scalaSource in Test).value :: Nil
  else
    (unmanagedSourceDirectories in Test).value
}

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.14",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.14" % "test",
  "org.zeromq" % "jzmq" % "3.1.0" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test"
)

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

publishTo <<= version { (v: String) =>
  if (v.trim.endsWith("-SNAPSHOT"))
    Some(Resolver.file("Snapshots", file("../mdialog.github.com/snapshots/")))
  else
    Some(Resolver.file("Releases", file("../mdialog.github.com/releases/")))
}
