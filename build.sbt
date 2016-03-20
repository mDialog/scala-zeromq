
name := "scala-zeromq"

organization := "com.mdialog"

version := "1.2.0"

scalaVersion := "2.11.7"

parallelExecution := false

scalacOptions ++= Seq("-unchecked", "-deprecation", "-language:postfixOps")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.14",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.14" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test"
)

//publish with: activator '+ publish-signed'
publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>http://www.github.com/mdialog/scala-zeromq</url>
  <licenses>
    <license>
      <name>Apache2</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:mdialog/scala-zeromq</url>
    <connection>scm:git:git@github.com:jasongoodwin/better-java-monads.git</connection>
  </scm>
  <developers>
    <developer>
      <id>mdialog</id>
      <name>mDialog</name>
      <url>http://www.github.com/mdialog</url>
    </developer>
  </developers>
)

credentials += Credentials(Path.userHome / ".nexuscredentials")
