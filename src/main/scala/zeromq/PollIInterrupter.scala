package zeromq

import akka.actor.{ Actor, Props }
import org.zeromq.{ ZMQ, ZMQException }
import akka.util.ByteString

private[zeromq] case object Interrupt
private[zeromq] case class ConnectToManager(address: String)

private[zeromq] object PollInterrupter {
  def apply(zmqContext: ZMQ.Context): Props =
    Props(classOf[PollInterrupter], zmqContext)
}

private[zeromq] class PollInterrupter(zmqContext: ZMQ.Context) extends Actor {
  private val config = context.system.settings.config
  private val socket = zmqContext.socket(ZMQ.PUB)
  private val address = config.getString("zeromq.poll-interrupt-socket")
  private val message = Array.empty[Byte]

  self ! ConnectToManager(address)

  def receive = {
    case ConnectToManager(address) ⇒
      try {
        socket.connect(address)
      } catch {
        case e: ZMQException ⇒ self ! ConnectToManager(address)
      }
    case Interrupt ⇒ socket.send(message, ZMQ.NOBLOCK)
  }

  override def postStop = {
    socket.close
    zmqContext.term
  }
}