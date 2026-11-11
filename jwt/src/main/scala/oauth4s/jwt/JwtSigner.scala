package oauth4s.jwt

import java.util.Base64
import java.nio.charset.StandardCharsets
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{GeneralSecurityException, Key, KeyFactory, PrivateKey}

import oauth4s.internal.Base64Url
import oauth4s.json.{Json, JsonCodec}
import oauth4s.jwt.internal.Signatures

object JwtSigner:

  def sign(claims: Json, algorithm: JwsAlgorithm, key: Key, keyId: Option[String] = None)(using codec: JsonCodec): Either[JwtError, String] =
    val header = Json.Obj(Vector("alg" -> Json.str(algorithm.name), "typ" -> Json.str("JWT")) ++ keyId.map(id => "kid" -> Json.str(id)))
    val input  = s"${Base64Url.encode(codec.render(header))}.${Base64Url.encode(codec.render(claims))}"

    Signatures.sign(algorithm, key, input.getBytes(StandardCharsets.US_ASCII)).map(signature => s"$input.${Base64Url.encode(signature)}")

  def privateKey(pem: String, keyType: String): Either[JwtError, PrivateKey] =
    val body = pem.linesIterator.map(_.trim).filterNot(line => line.isEmpty || line.startsWith("-----")).mkString

    try Right(KeyFactory.getInstance(keyType).generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder.decode(body))))
    catch
      case _: IllegalArgumentException     => Left(JwtError.InvalidKey("the PEM body is not base64"))
      case error: GeneralSecurityException => Left(JwtError.InvalidKey(s"it is not a PKCS#8 $keyType key: ${error.getMessage}"))
