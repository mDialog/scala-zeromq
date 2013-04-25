# scala-zeromq

**Requires libzmq (v2.1.0 or greater) and either 
[JZMQ](https://github.com/zeromq/jzmq) or 
[zeromq-scala-binding](https://github.com/valotrading/zeromq-scala-binding)**

scala-zeromq facilitates communication use the [ZeroMQ](http://zeromq.org) 
messaging library. ZeroMQ is a message-orient socket communication library that
support several high-level messaging patterns, including request-reply, 
publish-subscribe and push-pull. For a thorough description of how ZeroMQ works, 
read the [guide](http://zguide.zeromq.org). 

Unlike many ZeroMQ libraries, scala-zeromq provides a *completely threadsafe*
ZeroMQ socket interface. All socket communications are conducted using an
immutable handle called a SocketRef. Under the hood, scala-zeromq uses
[Akka](http://akka.io) to ensure all socket interactions are handled safely and
efficiently.

## Using

In your build.sbt

    resolvers += "mDialog snapshots" at "http://mdialog.github.io/snapshots/"

    libraryDependencies += "com.mdialog" %% "scala-zeromq" % "0.2.0-SNAPSHOT"

To get started, create some sockets.

    val pushSocket = ZeroMQ.socket(SocketType.Push)
    val pullSocket = ZeroMQ.socket(SocketType.Pull)

Then, bind one to a socket address and connect the other. ZeroMQ supports 
several message transport protocols in socket addresses.

    pushSocket.bind("tcp://localhost:5560")
    pullSocket.connect("tcp://localhost:5560")

Then, send and receive messages. scala-zeromq messages are an IndexedSeq of Akka
[ByteString](http://doc.akka.io/api/akka/snapshot/#akka.util.ByteString) 
objects.

    pushSocket.send(Message(ByteString("one"), ByteString("two")))

    val message = pullSocket.recv // blocks indefinitely awaiting message
    // message: zeromq.Message = Message(ByteString("one"), ByteString("two")))


If you'd like to stop receiving messages on a socket, close it.

    pushSocket.close

## Using with Akka

scala-zeromq is implemented as an Akka Extension. To use it with Akka,
load the extension either manually or automatically (through application 
configuration). 

Use the extension to request a new socket. Assign an ActorRef as listener if you
expect the socket to receive messages.

    val zmq = ZeroMQExtension(system)

    val pushSocket = zmq.newSocket(SocketType.Push, Bind("tcp://localhost:5560"))
    val pullSocket = zmq.newSocket(SocketType.Pull, Connect("tcp://localhost:5560"), Listener(anActorRef))

To send messages over the socket, send them to the socket actor.

    pushSocket ! Message(ByteString("one"), ByteString("two"))

Messages are received as zeromq.Message objects sent to the actor assigned as
each socket's listener.

You can get and set options after socket creation by sending messages to the 
socket actor.

    pushSocket ! Rate(100) // fire and forget
    pushSocket ? Rate(100) // return a future, await result to ensure change takes effect

    pushSocket ? Rate // returns Rate option

Close the socket by sending a PoisonPill to the socket actor.

    pullSocket ! PoisonPill

## Documentation

Read the API documentation here: [http://mdialog.github.io/api/scala-zeromq-0.2.0/](http://mdialog.github.io/api/scala-zeromq-0.2.0/)

## License

This project is released under the Apache License v2, for more details see the 'LICENSE' file.

## Contributing

Fork the project, add tests if possible and send a pull request.

## Contributors

Chris Dinn, Sebastian Hubbard

**©2013 mDialog Corp. All rights reserved.**
