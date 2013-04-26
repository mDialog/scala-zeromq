package zeromq

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import org.zeromq.ZMQ

object ZeroMQExtension extends ExtensionId[ZeroMQExtension] with ExtensionIdProvider {
  override def get(system: ActorSystem): ZeroMQExtension = super.get(system)
  def lookup(): this.type = this
  override def createExtension(system: ExtendedActorSystem): ZeroMQExtension = new ZeroMQExtension(system)
}

class ZeroMQExtension(val system: ActorSystem) extends Extension {

  private val zmqContext = ZMQ.context(1)

  implicit val newSocketTimeout = Timeout(Duration(system.settings.config.getMilliseconds("zeromq.new-socket-timeout"), TimeUnit.MILLISECONDS))

  val pollInterrupter = system.actorOf(PollInterrupter(zmqContext).withDispatcher("zeromq.poll-interrupter-dispatcher"), "zeromq-poll-interrupter")
  val socketManager = system.actorOf(SocketManager(zmqContext, pollInterrupter).withDispatcher("zeromq.socket-manager-dispatcher"), "zeromq-socket-manager")

  def newSocket(socketType: SocketType, socketParams: Param*)(implicit context: ActorContext = null): ActorRef = {
    val newSocketFuture = socketManager ? NewSocket(socketType, socketParams, context)

    pollInterrupter ! Interrupt

    Await.result(newSocketFuture.mapTo[ActorRef], newSocketTimeout.duration)
  }
}