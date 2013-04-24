package zeromq

import akka.actor._
import akka.pattern.{ ask, pipe }
import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import akka.util.{ ByteString, Timeout }

private[zeromq] object SocketHandler {
  def apply(socketManager: ActorRef, pollInterrupter: ActorRef, socketType: SocketType, socketParams: Seq[Param]): Props =
    Props(classOf[SocketHandler], socketManager, pollInterrupter, socketType, socketParams)
}

private[zeromq] class SocketHandler(manager: ActorRef, pollInterrupter: ActorRef, socketType: SocketType, socketParams: Seq[Param]) extends Actor {
  import context.dispatcher

  implicit val timeout = Timeout(Duration(context.system.settings.config.getMilliseconds("zeromq.new-socket-timeout"), TimeUnit.MILLISECONDS))

  private var listener: Option[ActorRef] = socketParams.collect({ case Listener(l) ⇒ l }).headOption

  private val params = socketParams.collect({ case a: SocketParam ⇒ a }).to[collection.immutable.Seq]

  private val registration = manager ? NewSocket(self, socketType, params)
  pollInterrupter ! Interrupt
  Await.result(registration, timeout.duration)

  def receive = {
    case Listener(l) ⇒
      listener map (context.unwatch(_))
      context.watch(l)
      listener = Some(l)

    case Terminated(l) ⇒
      if (listener == Some(l)) listener = None

    case param: SocketParam ⇒
      manager ! param
      pollInterrupter ! Interrupt

    case query: SocketOptionQuery ⇒
      manager ? query pipeTo sender
      pollInterrupter ! Interrupt

    case message: Message ⇒
      sender match {
        case `manager` ⇒ notifyListener(message)
        case _ ⇒
          manager ! message
          pollInterrupter ! Interrupt
      }
  }

  override def postStop: Unit = notifyListener(Closed)

  private def notifyListener(message: Any): Unit = listener map (_ ! message)
}
