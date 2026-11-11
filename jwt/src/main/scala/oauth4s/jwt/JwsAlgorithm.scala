package oauth4s.jwt

import java.security.MessageDigest
import java.nio.charset.StandardCharsets

import oauth4s.internal.Base64Url

enum JwsAlgorithm(val name: String, val digest: String, private[jwt] val jca: String, private[jwt] val family: JwsAlgorithm.Family):
  case RS256 extends JwsAlgorithm("RS256", "SHA-256", "SHA256withRSA", JwsAlgorithm.Family.Rsa)
  case RS384 extends JwsAlgorithm("RS384", "SHA-384", "SHA384withRSA", JwsAlgorithm.Family.Rsa)
  case RS512 extends JwsAlgorithm("RS512", "SHA-512", "SHA512withRSA", JwsAlgorithm.Family.Rsa)
  case PS256 extends JwsAlgorithm("PS256", "SHA-256", "RSASSA-PSS", JwsAlgorithm.Family.Pss)
  case PS384 extends JwsAlgorithm("PS384", "SHA-384", "RSASSA-PSS", JwsAlgorithm.Family.Pss)
  case PS512 extends JwsAlgorithm("PS512", "SHA-512", "RSASSA-PSS", JwsAlgorithm.Family.Pss)
  case ES256 extends JwsAlgorithm("ES256", "SHA-256", "SHA256withECDSAinP1363Format", JwsAlgorithm.Family.Ec)
  case ES384 extends JwsAlgorithm("ES384", "SHA-384", "SHA384withECDSAinP1363Format", JwsAlgorithm.Family.Ec)
  case ES512 extends JwsAlgorithm("ES512", "SHA-512", "SHA512withECDSAinP1363Format", JwsAlgorithm.Family.Ec)
  case HS256 extends JwsAlgorithm("HS256", "SHA-256", "HmacSHA256", JwsAlgorithm.Family.Hmac)
  case HS384 extends JwsAlgorithm("HS384", "SHA-384", "HmacSHA384", JwsAlgorithm.Family.Hmac)
  case HS512 extends JwsAlgorithm("HS512", "SHA-512", "HmacSHA512", JwsAlgorithm.Family.Hmac)

  def isSymmetric: Boolean = family == JwsAlgorithm.Family.Hmac

  def leftHash(value: String): String =
    val hashed = MessageDigest.getInstance(digest).digest(value.getBytes(StandardCharsets.US_ASCII))

    Base64Url.encode(hashed.take(hashed.length / 2))

object JwsAlgorithm:
  private[jwt] enum Family:
    case Rsa, Pss, Ec, Hmac

  val Asymmetric: Set[JwsAlgorithm] = values.filterNot(_.isSymmetric).toSet

  def parse(name: String): Option[JwsAlgorithm] = values.find(_.name == name)
