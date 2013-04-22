package zeromq

import akka.actor._
import akka.pattern.{ ask, pipe }
import akka.dispatch.Await
import akka.util.Duration
import java.util.concurrent.TimeUnit
import akka.util.{ ByteString, Timeout }

private[zeromq] class SocketHandler(manager: ActorRef, socketType: SocketType, socketParams: Seq[Param]) extends Actor {
  import context.dispatcher

  implicit val timeout = Timeout(Duration(context.system.settings.config.getMilliseconds("zeromq.new-socket-timeout"), TimeUnit.MILLISECONDS))

  var listener: Option[ActorRef] = socketParams.find {
    case l: Listener ⇒ true
    case _           ⇒ false
  } match {
    case Some(Listener(l)) ⇒ Some(l)
    case _                 ⇒ None
  }

  val params = socketParams.collect({ case a: SocketParam ⇒ a })

  Await.result(manager.ask(NewSocket(self, socketType, params)), timeout.duration)

  def receive = {
    case Listener(l) ⇒
      listener map (context.unwatch(_))
      context.watch(l)
      listener = Some(l)

    case Terminated(l) ⇒
      if (listener == Some(l)) listener = None

    case param: SocketParam       ⇒ manager ! param

    case query: SocketOptionQuery ⇒ manager ? query pipeTo sender

    case message: Message ⇒
      sender match {
        case `manager` ⇒ notifyListener(message)
        case _         ⇒ manager ! message
      }
  }

  override def postStop: Unit = notifyListener(Closed)

  private def notifyListener(message: Any): Unit = listener map (_ ! message)
}
