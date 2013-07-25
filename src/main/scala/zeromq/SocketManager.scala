package zeromq

import annotation.tailrec
import org.zeromq.{ ZMQ, ZMQException }
import akka.actor.{ Actor, ActorContext, ActorRef, Props, Terminated, Status }
import scala.concurrent.duration._
import scala.util.{ Try, Success, Failure }
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private[zeromq] case class NewSocket(socketType: SocketType, options: Seq[Param], context: ActorContext = null)
private[zeromq] case object Poll

case object Closed

private[zeromq] object SocketManager {
  def apply(zmqContext: ZMQ.Context, interrupter: ActorRef): Props =
    Props(classOf[SocketManager], zmqContext, interrupter)
}

private[zeromq] class SocketManager(zmqContext: ZMQ.Context, interrupter: ActorRef) extends Actor {

  private val config = context.system.settings.config
  private val poller: ZMQ.Poller = zmqContext.poller
  private val socketCount = new AtomicInteger()
  private val sockets = collection.mutable.Map.empty[ActorRef, Socket]

  val interruptListener = zmqContext.socket(ZMQ.SUB)
  val interruptListenerPollIndex = poller.register(interruptListener, ZMQ.Poller.POLLIN)

  interruptListener.bind(config.getString("zeromq.poll-interrupt-socket"))
  interruptListener.subscribe(Array.empty[Byte])

  private val pollTimeoutSetting = config.getMilliseconds("zeromq.poll-timeout")

  private val pollTimeoutUnit =
    if (ZMQ.getMajorVersion >= 3)
      TimeUnit.MILLISECONDS
    else
      TimeUnit.MICROSECONDS

  private val pollTimeout =
    Duration(pollTimeoutSetting, "millis").toUnit(pollTimeoutUnit).toLong

  self ! Poll

  def receive = {
    case message: Message ⇒
      sockets.get(sender) map {
        case socket: Writeable ⇒
          socket.queueForSend(message)
      }

    case Poll ⇒
      if (poller.poll(pollTimeout) > 0) {
        sockets foreach { socketPair ⇒
          val (handler, socket) = socketPair

          if (socket.isReadable) socket.receive() foreach (handler ! _)
          if (socket.isWriteable) socket.send()
        }

        if (poller.pollin(interruptListenerPollIndex)) readInterrupts
      }

      self ! Poll

    case NewSocket(socketType, options, parentContext) ⇒

      val socketParams = options.collect({ case p: SocketParam ⇒ p })
      newSocket(socketType, socketParams) match {
        case Success(socket) ⇒

          val listener = options.collect({ case Listener(l) ⇒ l }).headOption
          val handler = Option(parentContext).getOrElse(context).actorOf(SocketHandler(self, interrupter, listener), "socket-handler-" + socketCount.getAndIncrement())

          context.watch(handler)
          sockets(handler) = socket

          sender ! handler

        case Failure(exception) ⇒
          sender ! Status.Failure(exception)
      }

    case Terminated(handler) ⇒
      sockets.get(handler) map (_.close)
      sockets -= handler

    case (handler: ActorRef, param: SocketParam) ⇒
      try {
        sockets.get(handler) map { socket ⇒
          param match {
            case o: ConnectOption ⇒ handleConnectOption(socket, o)
            case o: PubSubOption  ⇒ handlePubSubOption(socket, o)
            case o: SocketOption  ⇒ socket.setSocketOption(o)
          }
        }
        sender ! Success(handler)
      } catch {
        case e: ZMQException ⇒ sender ! Status.Failure(e)
      }

    case (handler: ActorRef, query: SocketOptionQuery) ⇒
      try {
        sockets.get(handler) map (_.getSocketOption(query)) map (sender ! _)
      } catch {
        case e: ZMQException ⇒ sender ! Status.Failure(e)
      }
  }

  override def postStop = {
    sockets.values.map(_.close)
    interruptListener.close
  }

  private def newSocket(socketType: SocketType, options: Seq[SocketParam]): Try[Socket] = {

    var socket: Socket = null

    try {

      socket = Socket(zmqContext, poller, socketType)
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
      Success(socket)
    } catch {
      case e: ZMQException ⇒
        socket.close
        Failure(e)
    }
  }

  @tailrec private def readInterrupts: Unit =
    interruptListener.recv(ZMQ.NOBLOCK) match {
      case null ⇒
      case _    ⇒ readInterrupts
    }

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
