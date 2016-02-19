package zeromq

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.zeromq.ZMQ

import scala.annotation.varargs
import scala.concurrent.Await

object ZeroMQExtension extends ExtensionId[ZeroMQExtension] with ExtensionIdProvider {
  def lookup() = ZeroMQExtension
  override def createExtension(system: ExtendedActorSystem): ZeroMQExtension = new ZeroMQExtension(system)
}

class ZeroMQExtension(val system: ActorSystem) extends Extension {

  private val zmqContext = ZMQ.context(1)

  zmqContext.setMaxSockets(system.settings.config.getInt("zeromq.maximum-sockets"))

  system.registerOnTermination {
    zmqContext.term()
  }

  private implicit val newSocketTimeout = Timeout(system.settings.config.
    getDuration("zeromq.new-socket-timeout", TimeUnit.MILLISECONDS),
    TimeUnit.MILLISECONDS)

  private val poller: ZMQ.Poller = new ZMQ.Poller(32)

  private val interruptAddress = system.settings.config.getString("zeromq.poll-interrupt-socket")
  private val interruptSub: ZMQ.Socket = zmqContext.socket(ZMQ.SUB)
  private val interruptPub = zmqContext.socket(ZMQ.PUB)

  interruptSub.bind(interruptAddress)
  interruptSub.subscribe(Array.empty[Byte])
  interruptPub.connect(interruptAddress)

  private val pollIndex: Int = poller.register(interruptSub, ZMQ.Poller.POLLIN)
  private val pollInterrupter = system.actorOf(PollInterrupter(interruptPub).withDispatcher("zeromq.poll-interrupter-dispatcher"), "zeromq-poll-interrupter")
  private val socketManager = system.actorOf(SocketManager(zmqContext, poller, interruptSub, pollIndex, pollInterrupter).withDispatcher("zeromq.socket-manager-dispatcher"), "zeromq-socket-manager")

  @varargs def newSocket(socketType: SocketType, socketParams: Param*)(implicit context: ActorContext = null): ActorRef = {
    val newSocketFuture = socketManager ? NewSocket(socketType, socketParams, context)

    pollInterrupter ! Interrupt

    Await.result(newSocketFuture.mapTo[ActorRef], newSocketTimeout.duration)
  }
}
