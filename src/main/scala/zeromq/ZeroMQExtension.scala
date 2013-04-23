package zeromq

import akka.actor._
import org.zeromq.ZMQ
import java.util.concurrent.atomic.AtomicInteger

object ZeroMQExtension extends ExtensionId[ZeroMQExtension] with ExtensionIdProvider {
  override def get(system: ActorSystem): ZeroMQExtension = super.get(system)
  def lookup(): this.type = this
  override def createExtension(system: ExtendedActorSystem): ZeroMQExtension = new ZeroMQExtension(system)
}

class ZeroMQExtension(system: ActorSystem) extends Extension {

  private val zmqContext = ZMQ.context(1)
  private val socketCount = new AtomicInteger()

  val pollInterrupter = system.actorOf(PollInterrupter(zmqContext).withDispatcher("zeromq.poll-interrupter-dispatcher"), "zeromq-poll-interrupter")
  val socketManager = system.actorOf(SocketManager(zmqContext).withDispatcher("zeromq.socket-manager-dispatcher"), "zeromq-socket-manager")

  def newSocket(socketType: SocketType, socketParams: Param*): ActorRef =
    system.actorOf(SocketHandler(socketManager, pollInterrupter, socketType, socketParams), "socket-handler-" + socketCount.getAndIncrement())
}
