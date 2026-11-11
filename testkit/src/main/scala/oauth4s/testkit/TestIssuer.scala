package oauth4s.testkit

import java.time.Instant
import java.security.interfaces.RSAPublicKey
import java.security.{KeyPair, KeyPairGenerator}

import oauth4s.internal.Base64Url
import oauth4s.json.{Json, JsonCodec}
import oauth4s.{ClientId, IdToken, Issuer}
import oauth4s.jwt.{Jwk, Jwks, JwsAlgorithm, JwtSigner}

final class TestIssuer private (val issuer: Issuer, val keyId: String, pair: KeyPair):
  val Lifetime: Long = 300

  def jwksJson: Json =
    val public = pair.getPublic.asInstanceOf[RSAPublicKey]

    Json.obj(
      "keys" -> Json.arr(
        Json.obj(
          "kty" -> Json.str("RSA"),
          "kid" -> Json.str(keyId),
          "use" -> Json.str("sig"),
          "alg" -> Json.str(JwsAlgorithm.RS256.name),
          "n"   -> Json.str(Base64Url.encode(unsigned(public.getModulus.toByteArray))),
          "e"   -> Json.str(Base64Url.encode(unsigned(public.getPublicExponent.toByteArray))),
        )
      )
    )

  def jwks: Jwks = Jwks.fromJson(jwksJson).fold(error => throw error.toThrowable, keys => keys)

  def key: Jwk = jwks.keys.head

  def sign(claims: Json)(using JsonCodec): String =
    JwtSigner.sign(claims, JwsAlgorithm.RS256, pair.getPrivate, Some(keyId)).fold(error => throw error.toThrowable, token => token)

  def idToken(audience: ClientId, subject: String, issuedAt: Instant, claims: (String, Json)*)(using JsonCodec): IdToken =
    val standard = Vector(
      "iss" -> Json.str(issuer.value),
      "sub" -> Json.str(subject),
      "aud" -> Json.str(audience.value),
      "iat" -> Json.num(issuedAt.getEpochSecond),
      "exp" -> Json.num(issuedAt.getEpochSecond + Lifetime),
    )

    IdToken(sign(Json.Obj(standard ++ claims)))

  private def unsigned(bytes: Array[Byte]): Array[Byte] = bytes.dropWhile(_ == 0)

object TestIssuer:
  val DefaultIssuer: Issuer = Issuer("https://issuer.example.com")

  def apply(issuer: Issuer = DefaultIssuer, keyId: String = "test-key"): TestIssuer =
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)

    new TestIssuer(issuer, keyId, generator.generateKeyPair())
