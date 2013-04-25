package zeromq

import org.scalatest.FunSpec
import org.zeromq.ZMQException
import akka.util.ByteString
import scala.concurrent.{ Await, Future, ExecutionContext }
import scala.concurrent.duration._
import java.util.concurrent.Executors

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
    it("should block indefinitely until a message is received") {
      val push = ZeroMQ.socket(SocketType.Push)
      push.bind("inproc://test-receive-message")

      val pull = ZeroMQ.socket(SocketType.Pull)
      pull.connect("inproc://test-receive-message")

      val executionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor)
      val recvFuture = Future(pull.recv)(executionContext)

      val message = Message(ByteString("test-receive-message-option"))
      push.send(message)

      assert(Await.result(recvFuture, 50.millis) === message)
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

  describe("setOption") {
    it("should set socket option") {
      val push = ZeroMQ.socket(SocketType.Push)

      push.setOption(Linger(0))

      assert(true)
    }
  }

  describe("getOption") {
    it("should get socket option") {
      val push = ZeroMQ.socket(SocketType.Push)

      push.setOption(Rate(100))

      assert(push.getOption(Rate) === 100)
    }
  }

}