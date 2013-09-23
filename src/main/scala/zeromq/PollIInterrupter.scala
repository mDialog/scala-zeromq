package zeromq

import akka.actor.{ Actor, Props }
import org.zeromq.{ ZMQ, ZMQException }
import akka.util.ByteString

private[zeromq] case object Interrupt
private[zeromq] case object ConnectToManager

private[zeromq] object PollInterrupter {
  def apply(socket: ZMQ.Socket): Props =
    Props(classOf[PollInterrupter], socket)
}

private[zeromq] class PollInterrupter(socket: ZMQ.Socket) extends Actor {

  private val message = Array.empty[Byte]

  def receive = {
    case Interrupt ⇒ socket.send(message, ZMQ.NOBLOCK)

  }

  override def postStop = {
    socket.close
  }
}