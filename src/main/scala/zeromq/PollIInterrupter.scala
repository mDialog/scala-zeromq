package zeromq

import akka.actor.{ Actor, Props }
import org.zeromq.{ ZMQ, ZMQException }
import akka.util.ByteString

private[zeromq] case object Interrupt
private[zeromq] case object ConnectToManager

private[zeromq] object PollInterrupter {
  def apply(zmqContext: ZMQ.Context): Props =
    Props(classOf[PollInterrupter], zmqContext)
}

private[zeromq] class PollInterrupter(zmqContext: ZMQ.Context) extends Actor {

  private val config = context.system.settings.config
  private val socket = zmqContext.socket(ZMQ.PUB)
  private val address = config.getString("zeromq.poll-interrupt-socket")
  private val message = Array.empty[Byte]

  def receive = {
    case ConnectToManager ⇒
      try {
        socket.connect(address)
        sender ! "OK"
      } catch {
        case e: ZMQException ⇒ self ! ConnectToManager
      }
    case Interrupt ⇒ socket.send(message, ZMQ.NOBLOCK)

  }

  override def postStop = {
    socket.close
  }
}