package zeromq

import akka.actor.Actor
import org.zeromq.ZMQ
import akka.util.ByteString

private[zeromq] case object Interrupt

private[zeromq] class PollInterrupter extends Actor {
  private val config = context.system.settings.config
  private val zmqContext = ZMQ.context(1)
  private val socket = zmqContext.socket(ZMQ.PUB)
  private val message = context.system.name.getBytes

  socket.bind(config.getString("zeromq.poll-interrupt-socket"))

  def receive = {
    case Interrupt ⇒ socket.send(message, ZMQ.NOBLOCK)
  }

  override def postStop = {
    socket.close
    zmqContext.term
  }
}