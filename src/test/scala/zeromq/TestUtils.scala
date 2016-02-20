
package zeromq

object TestUtils {
  /**
    * Get a free port and reuturn a zeromq TCP endpoint.
    * Similar to akka.SocketUtil.temporaryServerAddress which was observed to fail consistently in travis on JDK8
    *
    * @return a zeromq TCP endpoint on the local machine
    */
  def getEndpoint = "tcp://127.0.0.1:%s" format {
    val s = new java.net.ServerSocket(0);
    try s.getLocalPort finally s.close()
  }
}
