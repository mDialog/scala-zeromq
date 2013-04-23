package zeromq

import akka.actor._

object ZeroMQExtension extends ExtensionId[ZeroMQExtension] with ExtensionIdProvider {
  override def get(system: ActorSystem): ZeroMQExtension = super.get(system)
  def lookup(): this.type = this
  override def createExtension(system: ExtendedActorSystem): ZeroMQExtension = new ZeroMQExtension(system)
}

class ZeroMQExtension(system: ActorSystem) extends Extension {

  var socketCount = -1

  val pollInterrupter = system.actorOf(Props[PollInterrupter].withDispatcher("zeromq.poll-interrupter-dispatcher"), "zeromq-poll-interrupter")
  val socketManager = system.actorOf(Props[SocketManager].withDispatcher("zeromq.manager-dispatcher"), "zeromq-socket-manager")

  def newSocket(socketType: SocketType, socketParams: Param*): ActorRef = {
    socketCount += 1
    system.actorOf(Props(classOf[SocketHandler], socketManager, pollInterrupter, socketType, socketParams), "socket-handler-" + socketCount)
  }
}
