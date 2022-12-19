package app.softnetwork.account.model

import java.security.MessageDigest

/** Created by smanciot on 14/04/2018.
  */
object Hash {

  def md5Hash(text: String): String = MessageDigest
    .getInstance("MD5")
    .digest(text.getBytes)
    .map(0xff & _)
    .map { "%02x".format(_) }
    .foldLeft("") { _ + _ }

}
