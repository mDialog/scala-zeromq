package zeromq

import akka.actor._

object ZeroMQExtension extends ExtensionId[ZeroMQExtension] with ExtensionIdProvider {
  override def get(system: ActorSystem): ZeroMQExtension = super.get(system)
  def lookup(): this.type = this
  override def createExtension(system: ExtendedActorSystem): ZeroMQExtension = new ZeroMQExtension(system)
}

class ZeroMQExtension(system: ActorSystem) extends Extension {

  val socketManager = system.actorOf(Props[SocketManager].withDispatcher("zeromq.context-dispatcher"), "zeromq")
  var socketCount = -1

  def newSocket(socketType: SocketType, socketParams: Param*): ActorRef = {
    socketCount += 1
    system.actorOf(Props(new SocketHandler(socketManager, socketType, socketParams)), "socket-handler-" + socketCount)
  }
}
