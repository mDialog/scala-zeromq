package zeromq

import akka.util.ByteString
import scala.collection.IndexedSeqLike
import scala.collection.mutable.{ArrayBuffer, Builder}

class Message(parts: ByteString*) extends IndexedSeq[ByteString] with IndexedSeqLike[ByteString, Message] {
  private val underlying = parts.toIndexedSeq

  override def apply(idx: Int) = underlying(idx)

  override def length = underlying.length

  override def newBuilder: Builder[ByteString, Message] = 
    ArrayBuffer.empty[ByteString].mapResult(Message.apply)
}

object Message {
  def apply(parts: ByteString*) = new Message(parts: _*)

  def unapplySeq(message: Message) = IndexedSeq.unapplySeq(message)
}
