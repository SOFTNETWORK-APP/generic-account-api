package app.softnetwork.sbt.build

import java.net.{InetAddress, NetworkInterface, ServerSocket}

object Utils {

  def hostname: String = InetAddress.getLocalHost.getHostAddress

  def getCurrentIpAddress: String = {
    var ip: String = ""
    val en = NetworkInterface.getNetworkInterfaces
    while (en.hasMoreElements) {
      val ni = en.nextElement
      val ee = ni.getInetAddresses

      while (ee.hasMoreElements) {
        val ia = ee.nextElement
        if (ia.isSiteLocalAddress)
          ip = ia.getHostAddress
      }
    }
    ip
  }

  def availablePort: Int = {
    val socket = new ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port
  }

}
