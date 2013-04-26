package zeromq

import akka.actor._
import akka.pattern.{ ask, pipe }
import akka.dispatch.Await
import akka.util.Duration
import akka.util.duration._
import java.util.concurrent.TimeUnit
import akka.util.{ ByteString, Timeout }

private[zeromq] object SocketHandler {
  def apply(socketManager: ActorRef, pollInterrupter: ActorRef, listener: Option[ActorRef]): Props =
    Props(new SocketHandler(socketManager, pollInterrupter, listener))
}

private[zeromq] class SocketHandler(manager: ActorRef, pollInterrupter: ActorRef, var listener: Option[ActorRef]) extends Actor {
  import context.dispatcher

  implicit val timeout = Timeout(500.millis)

  def receive = {
    case message: Message ⇒
      sender match {
        case `manager` ⇒ notifyListener(message)
        case _ ⇒
          manager ! message
          pollInterrupter ! Interrupt
      }

    case Listener(l) ⇒
      listener map (context.unwatch(_))
      context.watch(l)
      listener = Some(l)

    case Terminated(l) ⇒
      if (listener == Some(l)) listener = None

    case param: SocketParam ⇒
      manager ? (self, param) pipeTo sender
      pollInterrupter ! Interrupt

    case query: SocketOptionQuery ⇒
      manager ? (self, query) pipeTo sender
      pollInterrupter ! Interrupt
  }

  override def postStop: Unit = notifyListener(Closed)

  private def notifyListener(message: Any): Unit = listener map (_ ! message)
}
