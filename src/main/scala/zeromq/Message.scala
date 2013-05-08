package zeromq

import akka.util.ByteString

class Message(parts: ByteString*) extends IndexedSeq[ByteString] {
  private val vector = Vector(parts: _*)

  def apply(idx: Int) = vector(idx)

  def length = vector.length

  override def tail = Message(vector.tail: _*)
}

object Message {
  def apply(parts: ByteString*) = new Message(parts: _*)

  def unapplySeq(message: Message) = IndexedSeq.unapplySeq(message)
}