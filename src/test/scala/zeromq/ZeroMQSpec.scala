package zeromq

import org.scalatest.FunSpec

class ZeroMQSpec extends FunSpec {

  describe("socket") {
    it("should create SocketRef by socket type") {
      val socket = ZeroMQ.socket(SocketType.Pub)

      assert(socket.isInstanceOf[SocketRef])
    }
  }

}