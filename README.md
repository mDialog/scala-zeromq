# scala-zeromq

Requires libzmq (v2.1.0 or greater) and libjzmq.

An Akka zeromq library loosely based on Akka's included zeromq support but with 
an architeture better reflecting zeromq's intended usage.

scala-zeromq uses one zeromq context per ActorSystem, with a single actor using
a PinnedDispatcher polling all active sockets. Sockets are polled selectively to
make efficient use of CPU time.

Find scaladoc [here](http://mdialog.github.io/api/scala-zeromq-0.0.2/).

To use it:

    resolvers += "mDialog snapshots" at "http://mdialog.github.com/snapshots/"

    libraryDependencies += "com.mdialog" %% "scala-zeromq" % "0.0.2-SNAPSHOT"
