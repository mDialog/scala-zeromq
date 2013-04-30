package zeromq

import org.scalatest.FunSpec
import org.zeromq.ZMQException
import akka.util.ByteString
import scala.concurrent.{ Await, Future, ExecutionContext }
import scala.concurrent.duration._
import java.util.concurrent.TimeoutException

class SocketRefSpec extends FunSpec {

  describe("bind") {
    it("should bind to address") {
      val socket = ZeroMQ.socket(SocketType.Pub)

      socket.bind("inproc://test-bind-to-address")

      assert(true)
    }

    it("should raise error if bind does not succeed") {
      val router = ZeroMQ.socket(SocketType.Router)
      val dealer = ZeroMQ.socket(SocketType.Dealer)

      router.bind("inproc://test-bind-address-already-in-use")

      intercept[ZMQException] {
        dealer.bind("inproc://test-bind-address-already-in-use")
      }
    }
  }

  describe("connect") {
    it("should connect to address") {
      val router = ZeroMQ.socket(SocketType.Router)
      val dealer = ZeroMQ.socket(SocketType.Dealer)

      router.bind("inproc://test-connect-to-address")

      dealer.connect("inproc://test-connect-to-address")

      assert(true)
    }

    it("should raise error if connect does not succeed") {
      val router = ZeroMQ.socket(SocketType.Router)

      intercept[ZMQException] {
        // inproc address muct be bound before connect will succeed
        router.connect("inproc://test-connect-to-unbornd-inprc-address")
      }
    }
  }

  describe("send") {
    it("should send message") {
      val pub = ZeroMQ.socket(SocketType.Pub)

      pub.send(Message(ByteString("testing"), ByteString(123)))

      assert(true)
    }
  }

  describe("subscribe") {
    it("should accept String topic for sub socket") {
      val sub = ZeroMQ.socket(SocketType.Sub)

      sub.subscribe("test")

      assert(true)
    }

    it("should accept ByteString topic for sub socket") {
      val sub = ZeroMQ.socket(SocketType.Sub)

      sub.subscribe(ByteString("test"))

      assert(true)
    }
  }

  describe("recv") {

    def socketPair(address: String) = {
      val push = ZeroMQ.socket(SocketType.Push)
      push.bind(address)

      val pull = ZeroMQ.socket(SocketType.Pull)
      pull.connect(address)

      (push, pull)
    }

    it("should return future resulting in received message") {
      val (push, pull) = socketPair("inproc://test-receive-message")

      val recvFuture = pull.recv()

      val message = Message(ByteString("test-receive-message"))
      push.send(message)

      assert(Await.result(recvFuture, 50.millis) === message)
    }

    it("should not lose message when future times out") {
      val (push, pull) = socketPair("inproc://test-receive-timeout")

      val recvTimeout = pull.recv(20.millis)
      intercept[TimeoutException] { Await.result(recvTimeout, 500.millis) }

      val message = Message(ByteString("test-receive-timeout"))
      push.send(message)

      val recv = pull.recv()

      assert(Await.result(recv, 100.millis) === message)
    }
  }

  describe("recvOption") {
    it("should return None when no message waiting") {
      val sub = ZeroMQ.socket(SocketType.Sub)

      assert(sub.recvOption === None)
    }

    it("should receive message from socket when one is waiting") {
      val push = ZeroMQ.socket(SocketType.Push)
      push.bind("inproc://test-receive-message-option")

      val pull = ZeroMQ.socket(SocketType.Pull)
      pull.connect("inproc://test-receive-message-option")

      val message = Message(ByteString("test-receive-message-option"))
      push.send(message)

      Thread.sleep(50) // Give message time to arrive

      assert(pull.recvOption === Some(message))
    }
  }

  describe("setSocketOption") {
    it("should set socket option") {
      val push = ZeroMQ.socket(SocketType.Push)

      push.setSocketOption(Linger(0))

      assert(true)
    }
  }

  describe("getSocketOption") {
    it("should get socket option") {
      val push = ZeroMQ.socket(SocketType.Push)

      push.setSocketOption(Rate(100))

      assert(push.getSocketOption(Rate) === 100)
    }
  }

}