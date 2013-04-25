package zeromq

import akka.actor.ActorSystem

object ZeroMQ {

  implicit lazy val extension = ZeroMQExtension(ActorSystem("ZeroMQ"))

  def socket(socketType: SocketType) = SocketRef(socketType)

}