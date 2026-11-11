package oauth4s.jwt

import java.util.Base64
import java.time.Instant
import javax.crypto.spec.SecretKeySpec
import java.security.spec.ECGenParameterSpec
import java.security.{Key, KeyPair, KeyPairGenerator}
import java.security.interfaces.{ECPublicKey, RSAPublicKey}

import scala.util.Try

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.json.Json
import oauth4s.http.HttpResponse
import oauth4s.internal.Base64Url
import oauth4s.{Effect, Endpoint, ManualTime, RecordingHttp}

import scala.concurrent.duration.given

final class JwtSpec extends AnyWordSpec, Matchers:
  import JwtSpec.*

  "a signed token" should {
    "verify with the key it was signed with, for every asymmetric family and for HMAC when it is allowed" in {
      val results = List[(JwsAlgorithm, (Key, Jwk))](
        JwsAlgorithm.RS256 -> (Rsa.getPrivate, rsaJwk(Rsa, "k1")),
        JwsAlgorithm.PS384 -> (Rsa.getPrivate, rsaJwk(Rsa, "k1")),
        JwsAlgorithm.ES256 -> (Ec.getPrivate, ecJwk(Ec, "k1")),
        JwsAlgorithm.HS512 -> (Secret, Jwk.symmetric(Secret.getEncoded, Some("k1"))),
      ).map { case (algorithm, (signingKey, jwk)) =>
        val token = JwtSigner.sign(claims(), algorithm, signingKey, Some("k1")).toOption.get

        JwtVerifier.verify(token, Jwks.of(jwk), Validation.withAlgorithms(algorithm), Now).map(_.claims.subject)
      }

      results shouldBe List.fill(4)(Right(Some("user-1")))
    }

    "be refused when a byte of it changed, when it names no algorithm, or when its key signs for another family" in {
      val token    = JwtSigner.sign(claims(), JwsAlgorithm.RS256, Rsa.getPrivate, Some("k1")).toOption.get
      val keys     = Jwks.of(rsaJwk(Rsa, "k1"))
      val forged   = token.split('.').toList match
        case header :: _ :: signature :: Nil => s"$header.${Base64Url.encode("""{"sub":"admin","exp":9999999999}""")}.$signature"
        case _                               => token
      val unsigned = s"${Base64Url.encode("""{"alg":"none"}""")}.${Base64Url.encode("""{"sub":"admin"}""")}."
      val confused = JwtSigner.sign(claims(), JwsAlgorithm.HS256, SecretKeySpec(Rsa.getPublic.getEncoded, "HmacSHA256"), Some("k1")).toOption.get

      (
        JwtVerifier.verify(forged, keys, Validation, Now),
        JwtVerifier.verify(unsigned, keys, Validation, Now),
        JwtVerifier.verify(confused, keys, Validation, Now),
        JwtVerifier.verify(confused, keys, Validation.withAlgorithms(JwsAlgorithm.HS256), Now),
        JwtVerifier.verify("not.a.token", keys, Validation, Now).left.map(_.message.take(23)),
      ) shouldBe (
        Left(JwtError.InvalidSignature),
        Left(JwtError.UnsupportedAlgorithm("none")),
        Left(JwtError.AlgorithmNotAllowed(JwsAlgorithm.HS256)),
        Left(JwtError.UnknownKey(Some("k1"))),
        Left("the token is malformed:"),
      )
    }

    "be refused outside its lifetime, allowing for the leeway, and without an expiry unless that is waived" in {
      val keys    = Jwks.of(rsaJwk(Rsa, "k1"))
      val expired = signed(claims("exp" -> Json.num(Now.getEpochSecond - 120)))
      val recent  = signed(claims("exp" -> Json.num(Now.getEpochSecond - 30)))
      val future  = signed(claims("nbf" -> Json.num(Now.getEpochSecond + 300)))
      val endless = signed(Json.obj("sub" -> Json.str("user-1"), "iss" -> Json.str("https://issuer.example.com"), "aud" -> Json.str("client-1")))

      (
        JwtVerifier.verify(expired, keys, Validation, Now),
        JwtVerifier.verify(recent, keys, Validation, Now).isRight,
        JwtVerifier.verify(recent, keys, Validation.withLeeway(0.seconds), Now).isLeft,
        JwtVerifier.verify(future, keys, Validation, Now),
        JwtVerifier.verify(endless, keys, Validation, Now),
        JwtVerifier.verify(endless, keys, Validation.withoutExpiry, Now).isRight,
      ) shouldBe (
        Left(JwtError.Expired(Now.minusSeconds(120))),
        true,
        true,
        Left(JwtError.NotYetValid(Now.plusSeconds(300))),
        Left(JwtError.MissingClaim("exp")),
        true,
      )
    }

    "be refused from another issuer, for another audience, with another nonce or by a check of the caller's own" in {
      val keys  = Jwks.of(rsaJwk(Rsa, "k1"))
      val token = signed(claims("aud" -> Json.strings(List("client-1", "client-2")), "nonce" -> Json.str("n-1")))

      (
        JwtVerifier.verify(token, keys, Validation.withIssuer("https://other.example.com"), Now),
        JwtVerifier.verify(token, keys, Validation.withAudience("client-3"), Now),
        JwtVerifier.verify(token, keys, Validation.withNonce("n-2"), Now),
        JwtVerifier.verify(token, keys, Validation.withNonce("n-1"), Now).map(_.claims.audience),
        JwtVerifier.verify(token, keys, Validation.withCheck(_ => Left(JwtError.InvalidClaim("tid", "wrong tenant"))), Now),
      ) shouldBe (
        Left(JwtError.IssuerMismatch(Set("https://other.example.com"), Some("https://issuer.example.com"))),
        Left(JwtError.AudienceMismatch("client-3", Vector("client-1", "client-2"))),
        Left(JwtError.NonceMismatch),
        Right(Vector("client-1", "client-2")),
        Left(JwtError.InvalidClaim("tid", "wrong tenant")),
      )
    }

    "be refused when it requires an extension, and tried against every fitting key when it names none" in {
      val keys      = Jwks.of(rsaJwk(OtherRsa, "k0"), rsaJwk(Rsa, "k1"), ecJwk(Ec, "k2"))
      val header    = Base64Url.encode("""{"alg":"RS256","crit":["b64"],"b64":false}""")
      val critical  = s"$header.${Base64Url.encode("""{"sub":"user-1"}""")}.AAAA"
      val anonymous = JwtSigner.sign(claims(), JwsAlgorithm.RS256, Rsa.getPrivate).toOption.get

      (JwtVerifier.verify(critical, keys, Validation, Now), JwtVerifier.verify(anonymous, keys, Validation, Now).isRight) shouldBe (
        Left(JwtError.UnsupportedCritical(Vector("b64"))),
        true,
      )
    }
  }

  "a key set" should {
    "skip the keys it cannot use instead of failing as a whole" in {
      val text = s"""{"keys":[{"kty":"OKP","crv":"Ed25519","x":"AAAA","kid":"ed"},{"kty":"RSA","kid":"broken"},${render(rsaJwkJson(Rsa, "k1"))}]}"""

      Jwks.parse(text).map(_.keys.map(key => (key.keyId, key.key.toString))) shouldBe Right(
        Vector((Some("ed"), "Unsupported(OKP)"), (Some("k1"), "Rsa(2048 bits)"))
      )
    }

    "be fetched once, served from memory, and fetched again for an unknown key id, but not more often than the cooldown" in {
      val time            = ManualTime(Now)
      val rotated         = s"""{"keys":[${render(rsaJwkJson(OtherRsa, "k2"))}]}"""
      val http            = RecordingHttp.sequence(HttpResponse.json(s"""{"keys":[${render(rsaJwkJson(Rsa, "k1"))}]}"""), HttpResponse.json(rotated), HttpResponse.json(rotated))
      given Effect[Try]   = time
      given RecordingHttp = http
      val source          = JwksSource.remote[Try](Endpoint("https://issuer.example.com/jwks"), lifetime = 1.hour, cooldown = 1.minute)
      val old             = signed(claims())
      val fresh           = JwtSigner.sign(claims(), JwsAlgorithm.RS256, OtherRsa.getPrivate, Some("k2")).toOption.get

      val first      = (JwtVerifier.verify[Try](old, source, Validation).isSuccess, JwtVerifier.verify[Try](old, source, Validation).isSuccess, http.requests.size)
      val unknown    = (JwtVerifier.verify[Try](fresh, source, Validation).failed.map(_.getMessage).get, http.requests.size)
      time.advance(2.minutes)
      val afterwards = (JwtVerifier.verify[Try](fresh, source, Validation).isSuccess, http.requests.size)

      (first, unknown, afterwards) shouldBe (
        (true, true, 1),
        ("no key in the set matches key id 'k2'", 1),
        (true, 2),
      )
    }
  }

  "a private key" should {
    "be read from a PKCS#8 PEM, and say what is wrong with one that is not" in {
      val pem = s"-----BEGIN PRIVATE KEY-----\n${Base64.getMimeEncoder.encodeToString(Ec.getPrivate.getEncoded)}\n-----END PRIVATE KEY-----"

      (
        JwtSigner.privateKey(pem, "EC").map(_.getAlgorithm),
        JwtSigner.privateKey(pem, "RSA").left.map(_.message.take(40)),
        JwtSigner.privateKey("-----BEGIN PRIVATE KEY-----\n!!!\n-----END PRIVATE KEY-----", "EC"),
      ) shouldBe (
        Right("EC"),
        Left("the key cannot be used: it is not a PKCS"),
        Left(JwtError.InvalidKey("the PEM body is not base64")),
      )
    }
  }

object JwtSpec:
  val Now: Instant = Instant.parse("2026-01-01T00:00:00Z")

  val Rsa: KeyPair          = rsaPair()
  val OtherRsa: KeyPair     = rsaPair()
  val Ec: KeyPair           = ecPair()
  val Secret: SecretKeySpec = SecretKeySpec(Array.fill[Byte](64)(7), "HmacSHA512")

  val Validation: JwtValidation = JwtValidation.of("https://issuer.example.com", "client-1")

  def claims(extra: (String, Json)*): Json =
    val standard = Vector(
      "iss" -> Json.str("https://issuer.example.com"),
      "sub" -> Json.str("user-1"),
      "aud" -> Json.str("client-1"),
      "exp" -> Json.num(Now.getEpochSecond + 300),
    )

    Json.Obj(standard.filterNot((name, _) => extra.exists(_._1 == name)) ++ extra)

  def signed(payload: Json): String = JwtSigner.sign(payload, JwsAlgorithm.RS256, Rsa.getPrivate, Some("k1")).toOption.get

  def render(json: Json): String = json.render

  def rsaJwkJson(pair: KeyPair, keyId: String): Json =
    val public = pair.getPublic.asInstanceOf[RSAPublicKey]

    Json.obj(
      "kty" -> Json.str("RSA"),
      "kid" -> Json.str(keyId),
      "use" -> Json.str("sig"),
      "n"   -> Json.str(Base64Url.encode(public.getModulus.toByteArray.dropWhile(_ == 0))),
      "e"   -> Json.str(Base64Url.encode(public.getPublicExponent.toByteArray.dropWhile(_ == 0))),
    )

  def rsaJwk(pair: KeyPair, keyId: String): Jwk = Jwk.fromJson(rsaJwkJson(pair, keyId)).toOption.get

  def ecJwk(pair: KeyPair, keyId: String): Jwk =
    val point = pair.getPublic.asInstanceOf[ECPublicKey].getW

    Jwk(JwkKey.Ec("P-256", BigInt(point.getAffineX), BigInt(point.getAffineY)), Some(keyId), None, Some("sig"))

  private def rsaPair(): KeyPair =
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)
    generator.generateKeyPair()

  private def ecPair(): KeyPair =
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(ECGenParameterSpec("secp256r1"))
    generator.generateKeyPair()
