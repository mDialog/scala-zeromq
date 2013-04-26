
package zeromq

import language.postfixOps

import org.scalatest.matchers.MustMatchers
import org.scalatest.FunSpec
import akka.testkit.{ TestProbe, TestKit }

import scala.concurrent.duration._
import akka.util.{ ByteString, Timeout }
import akka.actor.{ Actor, ActorSystem, ActorRef, Cancellable }

// Much credit goes to akka-zeromq (https://github.com/akka/akka) for the 
// contents of this file.

class ZeroMQExtensionSpec extends TestKit(ActorSystem("ZeroMQExtensionSpec")) with FunSpec with MustMatchers {

  implicit val timeout: Timeout = Timeout(2 seconds)

  def zmq = ZeroMQExtension(system)

  describe("ZeroMQExtension") {
    it("should support pub-sub connections") {
      val endpoint = "tcp://127.0.0.1:%s" format { val s = new java.net.ServerSocket(0); try s.getLocalPort finally s.close() }

      val subscriberProbe = TestProbe()
      val publisher = zmq.newSocket(SocketType.Pub, Bind(endpoint))
      val subscriber = zmq.newSocket(SocketType.Sub, Listener(subscriberProbe.ref), Connect(endpoint), SubscribeAll)

      import system.dispatcher
      val msgGenerator = system.scheduler.schedule(100 millis, 10 millis, new Runnable {
        var number = 0
        def run() {
          publisher ! Message(ByteString(number.toString), ByteString.empty)
          number += 1
        }
      })

      val msgNumbers = subscriberProbe.receiveWhile(2 seconds) {
        case msg: Message if msg.length == 2 ⇒
          msg(1).length must be(0)
          msg
      }.map(m ⇒ m(0).utf8String.toInt)
      msgNumbers.length must be > 0
      msgNumbers must equal(for (i ← msgNumbers.head to msgNumbers.last) yield i)

      msgGenerator.cancel()

      system stop publisher
      system stop subscriber

      subscriberProbe.receiveWhile(1 seconds) {
        case msg ⇒ msg
      }.last must equal(Closed)
    }

    it("should support req-rep connections") {
      val endpoint = "tcp://127.0.0.1:%s" format { val s = new java.net.ServerSocket(0); try s.getLocalPort finally s.close() }

      val requesterProbe = TestProbe()
      val replierProbe = TestProbe()
      val requester = zmq.newSocket(SocketType.Req, Listener(requesterProbe.ref), Bind(endpoint))
      val replier = zmq.newSocket(SocketType.Rep, Listener(replierProbe.ref), Connect(endpoint))

      try {
        val request = Message(ByteString("Request"))
        val reply = Message(ByteString("Reply"))

        requester ! request
        replierProbe.expectMsg(request)
        replier ! reply
        requesterProbe.expectMsg(reply)
      } finally {
        system stop requester
        system stop replier
        replierProbe.expectMsg(Closed)
        requesterProbe.expectMsg(Closed)
      }
    }

    it("should support push-pull connections") {
      val endpoint = "tcp://127.0.0.1:%s" format { val s = new java.net.ServerSocket(0); try s.getLocalPort finally s.close() }

      val pullerProbe = TestProbe()
      val pusher = zmq.newSocket(SocketType.Push, Bind(endpoint))
      val puller = zmq.newSocket(SocketType.Pull, Listener(pullerProbe.ref), Connect(endpoint))

      val message = Message(ByteString("Pushed message"))

      pusher ! message
      pullerProbe.expectMsg(message)

      system stop pusher
      system stop puller
      pullerProbe.expectMsg(Closed)
    }

  }

  class MessageGeneratorActor(actorRef: ActorRef) extends Actor {
    var messageNumber: Int = 0
    var genMessages: Cancellable = null

    override def preStart() = {
      import system.dispatcher
      genMessages = system.scheduler.schedule(100 millis, 10 millis, self, "genMessage")
    }

    override def postStop() = {
      if (genMessages != null && !genMessages.isCancelled) {
        genMessages.cancel
        genMessages = null
      }
    }

    def receive = {
      case _ ⇒
        val payload = "%s".format(messageNumber)
        messageNumber += 1
        actorRef ! Message(ByteString(payload))
    }
  }
}
