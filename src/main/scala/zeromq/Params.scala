package zeromq

import akka.actor.ActorRef
import akka.util.ByteString

sealed trait Param

sealed trait HandlerParam extends Param

case class Listener(listener: ActorRef) extends HandlerParam

sealed trait SocketParam extends Param

sealed trait ConnectOption extends SocketParam {
  def endpoint: String
}

case class Connect(endpoint: String) extends ConnectOption

case class Bind(endpoint: String) extends ConnectOption

sealed trait PubSubOption extends SocketParam {
  def payload: ByteString
}

case class Subscribe(payload: ByteString) extends PubSubOption {
  def this(topic: String) = this(ByteString(topic))
}

object Subscribe {
  val all = Subscribe(ByteString.empty)
  def apply(topic: String): Subscribe = topic match {
    case null | "" ⇒ all
    case t         ⇒ new Subscribe(t)
  }
}

case class Unsubscribe(payload: ByteString) extends PubSubOption {
  def this(topic: String) = this(ByteString(topic))
}

object Unsubscribe {
  def apply(topic: String): Unsubscribe = new Unsubscribe(topic)
}

sealed trait SocketOption extends SocketParam
sealed trait SocketOptionQuery

case class Linger(value: Long) extends SocketOption
object Linger extends SocketOptionQuery {
  val no: Linger = Linger(0)
}

case class ReconnectIVL(value: Long) extends SocketOption
object ReconnectIVL extends SocketOptionQuery

case class ReconnectIVLMax(value: Long) extends SocketOption
object ReconnectIVLMax extends SocketOptionQuery

case class Backlog(value: Long) extends SocketOption
object Backlog extends SocketOptionQuery

case class MaxMsgSize(value: Long) extends SocketOption
object MaxMsgSize extends SocketOptionQuery

case class SendHighWaterMark(value: Long) extends SocketOption
object SendHighWaterMark extends SocketOptionQuery

case class ReceiveHighWaterMark(value: Long) extends SocketOption
object ReceiveHighWaterMark extends SocketOptionQuery

case class HighWaterMark(value: Long) extends SocketOption

case class Swap(value: Long) extends SocketOption
object Swap extends SocketOptionQuery

case class Affinity(value: Long) extends SocketOption
object Affinity extends SocketOptionQuery

case class Identity(value: Array[Byte]) extends SocketOption
object Identity extends SocketOptionQuery

case class Rate(value: Long) extends SocketOption
object Rate extends SocketOptionQuery

case class RecoveryInterval(value: Long) extends SocketOption
object RecoveryInterval extends SocketOptionQuery

case class MulticastLoop(value: Boolean) extends SocketOption
object MulticastLoop extends SocketOptionQuery

case class MulticastHops(value: Long) extends SocketOption
object MulticastHops extends SocketOptionQuery

case class SendBufferSize(value: Long) extends SocketOption
object SendBufferSize extends SocketOptionQuery

case class ReceiveBufferSize(value: Long) extends SocketOption
object ReceiveBufferSize extends SocketOptionQuery

object FileDescriptor extends SocketOptionQuery

case class Conflate(value: Boolean) extends SocketOption
object Conflate extends SocketOptionQuery