# scala-zeromq

scala-zeromq facilitates communication using the [ZeroMQ](http://zeromq.org) 
messaging library. ZeroMQ is a message-oriented socket communication library that
supports several high-level messaging patterns, including request-reply, 
publish-subscribe and push-pull. For a thorough description of how ZeroMQ works, 
read the [guide](http://zguide.zeromq.org). 

Unlike many ZeroMQ libraries, scala-zeromq provides a *completely threadsafe*
ZeroMQ socket interface. All socket communications are conducted using an
immutable handle called a SocketRef. Under the hood, scala-zeromq uses
[Akka](http://akka.io) to ensure all socket interactions are handled safely and
efficiently.

**Requires libzmq (v2.1.0 or greater) and either 
[JZMQ](https://github.com/zeromq/jzmq) or 
[zeromq-scala-binding](https://github.com/valotrading/zeromq-scala-binding)**, 
neither is included automatically.

## Using

In your build.sbt

    resolvers += "mDialog releases" at "http://mdialog.github.io/releases/"

    libraryDependencies += "com.mdialog" %% "scala-zeromq" % "1.0.0"

To get started with a quick example, create a few sockets:

    val pushSocket = ZeroMQ.socket(SocketType.Push)
    val pullSocket = ZeroMQ.socket(SocketType.Pull)

Then, bind one to a socket address and connect the other:

    pushSocket.bind("tcp://127.0.0.1:5560")
    pullSocket.connect("tcp://127.0.0.1:5560")

ZeroMQ supports several message transport protocols.

Next, send and receive a couple of messages. The `recv` method returns a Future 
containing a message if one arrives before the timeout is reached.

    pushSocket.send(Message(ByteString("one"), ByteString("two")))

    val message = pullSocket.recv() // returns Future, default timeout 1s
    Await.result(message, 1000.milliseconds)
    // message: zeromq.Message = Message(ByteString("one"), ByteString("two")))

    pushSocket.send(Message(ByteString("three"), ByteString("four")))

    pullSocket.recv(10.seconds) map (println(_)) // returns Future, timeout 10s
    // Message(ByteString("three"), ByteString("four")))

The `recvAll` method assigns a function to be called each time a message is 
received by the socket.

    pullSocket.recvAll { message: Message =>
      println("received: " + message.map(_.utf8String).mkString(" "))
    }
    pushSocket.send(Message(ByteString("five"), ByteString("six")))
    // received: five six

The `recvOption` method returns immediately with an option containing a message
if one is waiting to be received.

    pushSocket.send(Message(ByteString("seven"), ByteString("eight")))
    pullSocket.recvOption // returns immediately with message, if one is waiting
    // Option[zeromq.Message] = Some(Message(ByteString("seven"), ByteString("eight")))

A scala-zeromq Message is a collection of
[akka.util.ByteString](http://doc.akka.io/api/akka/snapshot/#akka.util.ByteString) 
objects. Each item in the collection contains one ZeroMQ message part.

If you'd like to stop receiving messages on a socket, close it.

    pushSocket.close

Once closed a socket it can no longer be used. If you need to send or receive
messages again you must create a new socket.

### Using with Akka

scala-zeromq is implemented as an 
[Akka Extension](http://doc.akka.io/docs/akka/snapshot/scala/extending-akka.html). 
To use it's fully asynchronous Akka interface, just load the extension. 

This branch uses Akka 2.2, the latest pre-release version of Akka. If you're
using Akka 2.1 see the [0.1.X](https://github.com/mDialog/scala-zeromq/tree/0.1.X)
branch. If you're using Akka 2.0, see the [0.0.X](https://github.com/mDialog/scala-zeromq/tree/0.0.X) 
branch.

Use the extension to request a new socket. Assign an ActorRef as listener if you
expect the socket to receive messages.

    val zmq = ZeroMQExtension(system)

    val pushSocket = zmq.newSocket(SocketType.Push, Bind("tcp://localhost:5560"))
    val pullSocket = zmq.newSocket(SocketType.Pull, Connect("tcp://localhost:5560"), Listener(anActorRef))

Each socket is a child of the actor responsible for creating it. 

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

Read the API documentation here: [http://mdialog.github.io/api/scala-zeromq-1.0.0/](http://mdialog.github.io/api/scala-zeromq-1.0.0/)

## License

This project is released under the Apache License v2, for more details see the 'LICENSE' file.

## Contributing

Fork the project, add tests if possible and send a pull request.

## Contributors

Chris Dinn, Sebastian Hubbard

**©2013 mDialog Corp. All rights reserved.**
