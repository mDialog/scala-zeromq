package zeromq

import akka.actor.{ Actor, Props }
import org.zeromq.ZMQ
import akka.util.ByteString

private[zeromq] case object Interrupt

private[zeromq] object PollInterrupter {
  def apply(zmqContext: ZMQ.Context): Props =
    Props(classOf[PollInterrupter], zmqContext)
}

private[zeromq] class PollInterrupter(zmqContext: ZMQ.Context) extends Actor {
  private val config = context.system.settings.config
  private val socket = zmqContext.socket(ZMQ.PUB)
  private val message = Array.empty[Byte]

  socket.bind(config.getString("zeromq.poll-interrupt-socket"))

  def receive = {
    case Interrupt ⇒ socket.send(message, ZMQ.NOBLOCK)
  }

  override def postStop = {
    socket.close
    zmqContext.term
  }
}