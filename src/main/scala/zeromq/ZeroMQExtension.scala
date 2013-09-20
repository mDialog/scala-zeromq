package zeromq

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import org.zeromq.ZMQ

object ZeroMQExtension extends ExtensionId[ZeroMQExtension] with ExtensionIdProvider {
  def lookup() = ZeroMQExtension
  override def createExtension(system: ExtendedActorSystem): ZeroMQExtension = new ZeroMQExtension(system)
}

class ZeroMQExtension(val system: ActorSystem) extends Extension {

  private val zmqContext = ZMQ.context(1)
  system.registerOnTermination {
    zmqContext.term
  }

  private implicit val newSocketTimeout = Timeout(Duration(system.settings.config.getMilliseconds("zeromq.new-socket-timeout"), TimeUnit.MILLISECONDS))

  private val poller: ZMQ.Poller = zmqContext.poller

  private val interruptAddress = system.settings.config.getString("zeromq.poll-interrupt-socket")
  private val interruptSub: ZMQ.Socket = zmqContext.socket(ZMQ.SUB)
  private val interruptPub = zmqContext.socket(ZMQ.PUB)

  interruptSub.bind(interruptAddress)
  interruptSub.subscribe(Array.empty[Byte])
  interruptPub.connect(interruptAddress)

  private val pollIndex: Int = poller.register(interruptSub, ZMQ.Poller.POLLIN)
  private val pollInterrupter = system.actorOf(PollInterrupter(interruptPub).withDispatcher("zeromq.poll-interrupter-dispatcher"), "zeromq-poll-interrupter")
  private val socketManager = system.actorOf(SocketManager(zmqContext, poller, interruptSub, pollIndex, pollInterrupter).withDispatcher("zeromq.socket-manager-dispatcher"), "zeromq-socket-manager")

  def newSocket(socketType: SocketType, socketParams: Param*)(implicit context: ActorContext = null): ActorRef = {
    val newSocketFuture = socketManager ? NewSocket(socketType, socketParams, context)

    pollInterrupter ! Interrupt

    Await.result(newSocketFuture.mapTo[ActorRef], newSocketTimeout.duration)
  }
}