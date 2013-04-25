package zeromq

import annotation.tailrec
import org.zeromq.ZMQ
import collection.mutable.ListBuffer
import akka.util.ByteString

class UnsupportedOperationException() extends Exception("Operation not supported by this socket type")

abstract class SocketType(val id: Int)

object SocketType {

  object Pub extends SocketType(ZMQ.PUB)

  object Sub extends SocketType(ZMQ.SUB)

  object Dealer extends SocketType(ZMQ.DEALER)

  object Router extends SocketType(ZMQ.ROUTER)

  object Req extends SocketType(ZMQ.REQ)

  object Rep extends SocketType(ZMQ.REP)

  object Push extends SocketType(ZMQ.PUSH)

  object Pull extends SocketType(ZMQ.PULL)

  object Pair extends SocketType(ZMQ.PAIR)
}

private[zeromq] sealed trait SocketState

private[zeromq] object SocketState {
  object Send extends SocketState
  object Receive extends SocketState
}

private[zeromq] abstract class Socket(val socket: ZMQ.Socket, val poller: ZMQ.Poller) {

  var pollIndex: Int = -1
  var pollMask: Int = 0

  def setPollMask(mask: Int) {
    if (pollMask != mask) {
      pollMask = mask
      if (pollIndex != -1)
        poller.unregister(socket)
      if (pollMask != 0)
        pollIndex = poller.register(socket, pollMask)
    }
  }

  def setPollFlag(flags: Int) = setPollMask(pollMask | flags)

  def clearPollFlag(flags: Int) = setPollMask(pollMask - (pollMask & flags))

  def canSend: Boolean

  def isReadable: Boolean = poller.pollin(pollIndex)

  def isWriteable: Boolean = canSend && poller.pollout(pollIndex)

  def receive(messages: IndexedSeq[Message] = Vector.empty): IndexedSeq[Message]
  def send(): Unit

  def connect(endpoint: String) = socket.connect(endpoint)

  def bind(endpoint: String) = socket.bind(endpoint)

  def setSocketOption(option: SocketOption) =
    option match {
      case Linger(value)               ⇒ socket.setLinger(value)
      case ReconnectIVL(value)         ⇒ socket.setReconnectIVL(value)
      case Backlog(value)              ⇒ socket.setBacklog(value)
      case ReconnectIVLMax(value)      ⇒ socket.setReconnectIVLMax(value)
      case MaxMsgSize(value)           ⇒ socket.setMaxMsgSize(value)
      case SendHighWaterMark(value)    ⇒ socket.setSndHWM(value)
      case ReceiveHighWaterMark(value) ⇒ socket.setRcvHWM(value)
      case HighWaterMark(value)        ⇒ socket.setHWM(value)
      case Swap(value)                 ⇒ socket.setSwap(value)
      case Affinity(value)             ⇒ socket.setAffinity(value)
      case Identity(value)             ⇒ socket.setIdentity(value)
      case Rate(value)                 ⇒ socket.setRate(value)
      case RecoveryInterval(value)     ⇒ socket.setRecoveryInterval(value)
      case MulticastLoop(value)        ⇒ socket.setMulticastLoop(value)
      case MulticastHops(value)        ⇒ socket.setMulticastHops(value)
      case SendBufferSize(value)       ⇒ socket.setSendBufferSize(value)
      case ReceiveBufferSize(value)    ⇒ socket.setReceiveBufferSize(value)
    }

  def getSocketOption(query: SocketOptionQuery) =
    query match {
      case Linger               ⇒ socket.getLinger
      case ReconnectIVL         ⇒ socket.getReconnectIVL
      case Backlog              ⇒ socket.getBacklog
      case ReconnectIVLMax      ⇒ socket.getReconnectIVLMax
      case MaxMsgSize           ⇒ socket.getMaxMsgSize
      case SendHighWaterMark    ⇒ socket.getSndHWM
      case ReceiveHighWaterMark ⇒ socket.getRcvHWM
      case Swap                 ⇒ socket.getSwap
      case Affinity             ⇒ socket.getAffinity
      case Identity             ⇒ socket.getIdentity
      case Rate                 ⇒ socket.getRate
      case RecoveryInterval     ⇒ socket.getRecoveryInterval
      case MulticastLoop        ⇒ socket.hasMulticastLoop
      case MulticastHops        ⇒ socket.getMulticastHops
      case SendBufferSize       ⇒ socket.getSendBufferSize
      case ReceiveBufferSize    ⇒ socket.getReceiveBufferSize
      case FileDescriptor       ⇒ socket.getFD
    }

  def close {
    poller.unregister(socket)
    socket.close
  }

}

private[zeromq] object Socket {
  import SocketType._

  def apply(context: ZMQ.Context, poller: ZMQ.Poller, socketType: SocketType) = {
    val socket = context.socket(socketType.id)

    socketType match {
      case Req    ⇒ new ReqSocket(socket, poller)
      case Rep    ⇒ new RepSocket(socket, poller)
      case Push   ⇒ new PushSocket(socket, poller)
      case Pull   ⇒ new PullSocket(socket, poller)
      case Pub    ⇒ new PubSocket(socket, poller)
      case Sub    ⇒ new SubSocket(socket, poller)
      case Pair   ⇒ new PairSocket(socket, poller)
      case Dealer ⇒ new DealerSocket(socket, poller)
      case Router ⇒ new RouterSocket(socket, poller)
    }
  }
}

private[zeromq] trait Writeable { self: Socket ⇒

  val sendBuffer = new ListBuffer[Message]

  @tailrec final def sendMessage(message: Message): Boolean =
    if (message.isEmpty) true
    else {
      val flags = if (message.tail.nonEmpty)
        ZMQ.NOBLOCK | ZMQ.SNDMORE
      else
        ZMQ.NOBLOCK

      if (socket.send(message.head.toArray, flags))
        sendMessage(message.tail)
      else {
        sendBuffer.prepend(message) // Re-queue unsent message
        false
      }
    }

  def send(): Unit =
    if (sendBuffer.nonEmpty) {
      if (sendMessage(sendBuffer.remove(0))) send
    } else {
      clearPollFlag(ZMQ.Poller.POLLOUT)
    }

  def queueForSend(message: Message) {
    sendBuffer.append(message)
    setPollFlag(ZMQ.Poller.POLLOUT)
  }

  def canSend = sendBuffer.nonEmpty
}

private[zeromq] trait Readable { self: Socket ⇒
  @tailrec final def receiveMessage(currentFrames: Vector[ByteString] = Vector.empty): Option[Message] =
    socket.recv(ZMQ.NOBLOCK) match {
      case null ⇒ None

      case bytes ⇒
        val frames = currentFrames :+ ByteString(bytes)

        if (socket.hasReceiveMore)
          receiveMessage(frames)
        else {
          Some(Message(frames: _*))
        }
    }

  def receive(messages: IndexedSeq[Message] = Vector.empty): IndexedSeq[Message] =
    receiveMessage() match {
      case None          ⇒ messages
      case Some(message) ⇒ receive(messages :+ message)
    }
}

private[zeromq] class AlternatingSocket(socket: ZMQ.Socket, poller: ZMQ.Poller, var state: SocketState)
    extends Socket(socket, poller) with Readable with Writeable {
  import SocketState._

  state match {
    case Send    ⇒
    case Receive ⇒ setPollFlag(ZMQ.Poller.POLLIN)
  }

  override def isReadable = state match {
    case Send    ⇒ false
    case Receive ⇒ super.isReadable
  }

  override def receive(messages: IndexedSeq[Message] = Vector.empty) = state match {
    case Send ⇒ throw new UnsupportedOperationException()
    case Receive ⇒
      receiveMessage() match {
        case Some(message) ⇒
          clearPollFlag(ZMQ.Poller.POLLIN)
          state = Send
          IndexedSeq(message)

        case None ⇒ messages
      }
  }

  override def canSend = state match {
    case Send    ⇒ super.canSend
    case Receive ⇒ false
  }

  override def queueForSend(message: Message) = state match {
    case Send    ⇒ super.queueForSend(message)
    case Receive ⇒ throw new UnsupportedOperationException()
  }

  override def send = state match {
    case Send ⇒
      if (sendBuffer.nonEmpty) {
        sendMessage(sendBuffer.remove(0))
        setPollFlag(ZMQ.Poller.POLLIN)
        state = Receive
      }

    case Receive ⇒ throw new UnsupportedOperationException()
  }
}

private[zeromq] trait Bidirectional extends Readable with Writeable {
  self: Socket ⇒

  setPollFlag(ZMQ.Poller.POLLIN)
}

private[zeromq] trait SendOnly extends Writeable {
  self: Socket ⇒

  override def isReadable = false

  def receive(messages: IndexedSeq[Message] = Vector.empty) = throw new UnsupportedOperationException()
}

private[zeromq] trait ReceiveOnly extends Readable {
  self: Socket ⇒

  setPollFlag(ZMQ.Poller.POLLIN)

  def canSend = false

  def send() = throw new UnsupportedOperationException()
}

private[zeromq] class ReqSocket(socket: ZMQ.Socket, poller: ZMQ.Poller) extends AlternatingSocket(socket, poller, SocketState.Send)

private[zeromq] class RepSocket(socket: ZMQ.Socket, poller: ZMQ.Poller) extends AlternatingSocket(socket, poller, SocketState.Receive)

private[zeromq] class PushSocket(socket: ZMQ.Socket, poller: ZMQ.Poller) extends Socket(socket, poller) with SendOnly

private[zeromq] class PullSocket(socket: ZMQ.Socket, poller: ZMQ.Poller) extends Socket(socket, poller) with ReceiveOnly

private[zeromq] class PubSocket(socket: ZMQ.Socket, poller: ZMQ.Poller) extends Socket(socket, poller) with SendOnly

private[zeromq] class SubSocket(socket: ZMQ.Socket, poller: ZMQ.Poller) extends Socket(socket, poller) with ReceiveOnly {
  def subscribe(topic: ByteString) = socket.subscribe(topic.toArray)

  def unsubscribe(topic: ByteString) = socket.unsubscribe(topic.toArray)
}

private[zeromq] class PairSocket(socket: ZMQ.Socket, poller: ZMQ.Poller) extends Socket(socket, poller) with Bidirectional

private[zeromq] class DealerSocket(socket: ZMQ.Socket, poller: ZMQ.Poller) extends Socket(socket, poller) with Bidirectional

private[zeromq] class RouterSocket(socket: ZMQ.Socket, poller: ZMQ.Poller) extends Socket(socket, poller) with Bidirectional
