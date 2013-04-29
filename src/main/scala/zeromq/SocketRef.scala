package zeromq

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import akka.actor.{ Actor, ActorRef, Props, Status, PoisonPill }
import akka.pattern.ask
import akka.util.{ ByteString, Timeout }
import java.util.concurrent.TimeoutException

private case object FetchMessage
private case class AwaitMessage(timeout: FiniteDuration)
private case class RecvTimeout(receiver: ActorRef)

private class SocketListener extends Actor {
  import context.dispatcher
  //import scala.concurrent.ExecutionContext.Implicits.global

  private val messageQueue = collection.mutable.Queue.empty[Message]
  private val waitingRecvs = collection.mutable.Set.empty[ActorRef]

  def receive = {
    case message: Message ⇒
      waitingRecvs.headOption match {
        case Some(next) ⇒
          waitingRecvs.remove(next)
          next ! message
        case None ⇒ messageQueue.enqueue(message)
      }

    case AwaitMessage(timeout) ⇒
      if (messageQueue.nonEmpty)
        sender ! messageQueue.dequeue
      else {
        waitingRecvs.add(sender)
        context.system.scheduler.scheduleOnce(timeout, self, RecvTimeout(sender))
      }

    case RecvTimeout(receiver) ⇒
      if (waitingRecvs.remove(receiver))
        receiver ! Status.Failure(new TimeoutException())

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

  def recv(timeoutDuration: FiniteDuration = 1000.millis): Future[Message] =
    (listener ? AwaitMessage(timeoutDuration))(Timeout(timeoutDuration * 2)).mapTo[Message]

  def recvOption: Option[Message] =
    Await.result((listener ? FetchMessage).mapTo[Option[Message]], timeout.duration)

  def close = socket ! PoisonPill
}