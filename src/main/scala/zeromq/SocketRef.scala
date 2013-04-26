package zeromq

import akka.dispatch.{ Await, Future }
import akka.util.Duration
import akka.actor.{ Actor, ActorRef, Props, Status, PoisonPill }
import akka.pattern.ask
import akka.util.{ ByteString, Timeout }

private case object AwaitMessage
private case object FetchMessage

private class SocketListener extends Actor {
  private val messageQueue = collection.mutable.Queue.empty[Message]
  private val receiverQueue = collection.mutable.Queue.empty[ActorRef]

  def receive = {
    case message: Message ⇒
      if (receiverQueue.nonEmpty)
        receiverQueue.dequeue ! message
      else
        messageQueue.enqueue(message)

    case AwaitMessage ⇒
      if (messageQueue.nonEmpty)
        sender ! messageQueue.dequeue
      else
        receiverQueue.enqueue(sender)

    case FetchMessage ⇒ sender ! messageQueue.dequeueFirst(_ ⇒ true)
  }
}

case class SocketRef(socketType: SocketType)(implicit extension: ZeroMQExtension) {
  import Status._

  private implicit val timeout = Timeout(1000)

  private val listener = extension.system.actorOf(Props[SocketListener])
  private val socket = extension.newSocket(socketType, Listener(listener))

  def bind(address: String): Unit =
    Await.result(socket ? Bind(address), timeout.duration)

  def connect(address: String): Unit =
    Await.result(socket ? Connect(address), timeout.duration)

  def setSocketOption(option: SocketOption): Unit =
    Await.result(socket ? option, timeout.duration)

  def getSocketOption(query: SocketOptionQuery) =
    Await.result(socket ? query, timeout.duration)

  def subscribe(topic: ByteString): Unit =
    Await.result(socket ? Subscribe(topic), timeout.duration)

  def subscribe(topic: String): Unit = subscribe(ByteString(topic))

  def unsubscribe(topic: ByteString): Unit =
    Await.result(socket ? Unsubscribe(topic), timeout.duration)

  def unsubscribe(topic: String): Unit = unsubscribe(ByteString(topic))

  def send(message: Message) = socket ! message

  def recv: Message =
    Await.result((listener ? AwaitMessage).mapTo[Message], Duration.Inf)

  def recvOption: Option[Message] =
    Await.result((listener ? FetchMessage).mapTo[Option[Message]], timeout.duration)

  def close = socket ! PoisonPill
}