package zeromq

object TestUtils {
  def getEndpoint = "tcp://127.0.0.1:%s" format { val s = new java.net.ServerSocket(0); try s.getLocalPort finally s.close() }
}
