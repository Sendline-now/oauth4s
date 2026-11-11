package oauth4s.internal

import java.security.SecureRandom

private[oauth4s] object Secrets:
  val StateBytes: Int    = 32
  val NonceBytes: Int    = 16
  val VerifierBytes: Int = 32

  private val random = SecureRandom()

  def token(bytes: Int): String =
    val buffer = new Array[Byte](bytes)
    random.nextBytes(buffer)
    Base64Url.encode(buffer)

  def isUnreserved(char: Char): Boolean =
    (char >= 'A' && char <= 'Z') || (char >= 'a' && char <= 'z') || (char >= '0' && char <= '9') || char == '-' || char == '.' || char == '_' || char == '~'
