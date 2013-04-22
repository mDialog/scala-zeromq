package zeromq

import org.zeromq.ZMQ
import akka.actor.{ Actor, ActorRef, Terminated }
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

case class NewSocket(handler: ActorRef, socketType: SocketType, options: Seq[SocketParam])
case object SocketCreated
case object Closed
case object Poll

private[zeromq] class SocketManager extends Actor {

  private val zmqContext = ZMQ.context(1)
  private val poller: ZMQ.Poller = zmqContext.poller

  private val pollTimeoutSetting =
    context.system.settings.config.getMilliseconds("zeromq.poll-timeout")

  private val pollTimeoutUnit =
    if (ZMQ.getMajorVersion >= 3)
      TimeUnit.MILLISECONDS
    else
      TimeUnit.MICROSECONDS

  private val pollTimeout =
    Duration(pollTimeoutSetting, "millis").toUnit(pollTimeoutUnit).toLong

  private val sockets = collection.mutable.Map.empty[ActorRef, Socket]

  self ! Poll

  def receive = {
    case NewSocket(handler, socketType, options) ⇒
      val socket = Socket(zmqContext, poller, socketType)

      // Perform intialization in order: socket options, connection options,
      // then pubsub options.
      val groupedOptions = options groupBy {
        case _: SocketOption  ⇒ "socket-options"
        case _: ConnectOption ⇒ "connect-options"
        case _: PubSubOption  ⇒ "pubsub-options"
      }

      groupedOptions.get("socket-options") map { options ⇒
        options foreach { option ⇒
          socket.setSocketOption(option.asInstanceOf[SocketOption])
        }
      }

      groupedOptions.get("connect-options") map { options ⇒
        options foreach { option ⇒
          handleConnectOption(socket, option.asInstanceOf[ConnectOption])
        }
      }

      groupedOptions.get("pubsub-options") map { options ⇒
        options foreach { option ⇒
          handlePubSubOption(socket, option.asInstanceOf[PubSubOption])
        }
      }

      sockets(handler) = socket
      context.watch(handler)
      sender ! SocketCreated

    case Terminated(handler) ⇒
      sockets.get(handler) map (_.close)
      sockets -= handler

    case Poll ⇒
      if (poller.poll(pollTimeout) > 0) {
        sockets foreach { socketPair ⇒
          val (handler, socket) = socketPair

          if (socket.isReadable) socket.receive() foreach (handler ! _)
          if (socket.isWriteable) socket.send()
        }
      }

      self ! Poll

    case message: Message ⇒
      sockets.get(sender) map {
        case socket: Writeable ⇒ socket.queueForSend(message)
      }

    case param: SocketParam ⇒
      sockets.get(sender) map { socket ⇒
        param match {
          case o: ConnectOption ⇒ handleConnectOption(socket, o)
          case o: PubSubOption  ⇒ handlePubSubOption(socket, o)
          case o: SocketOption  ⇒ socket.setSocketOption(o)
          case _                ⇒
        }
      }

    case query: SocketOptionQuery ⇒
      sockets.get(sender) map (_.getSocketOption(query)) map (sender ! _)
  }

  override def postStop = zmqContext.term

  private def handleConnectOption(socket: Socket, msg: ConnectOption): Unit =
    msg match {
      case Connect(endpoint) ⇒ socket.connect(endpoint)
      case Bind(endpoint)    ⇒ socket.bind(endpoint)
    }

  private def handlePubSubOption(socket: Socket, msg: PubSubOption): Unit =
    socket match {
      case subSocket: SubSocket ⇒
        msg match {
          case Subscribe(topic)   ⇒ subSocket.subscribe(topic)
          case Unsubscribe(topic) ⇒ subSocket.unsubscribe(topic)
        }
    }

}