package oauth4s.it.docs.jwt

import java.time.Instant
import java.security.PrivateKey

import cats.effect.IO

import oauth4s.Endpoint
import oauth4s.json.Json
import oauth4s.http.HttpClient
import oauth4s.http.jdk.JdkHttpClient
import oauth4s.jwt.{Jwks, JwksSource, JwsAlgorithm, Jwt, JwtError, JwtSigner, JwtValidation, JwtVerifier}

import scala.concurrent.duration.given
import oauth4s.runtime.cats.CatsInstances.given

given HttpClient[IO] = JdkHttpClient[IO]()

val keys: JwksSource[IO] = JwksSource.remote[IO](Endpoint("https://auth.example.com/jwks"))

val rules: JwtValidation =
  JwtValidation
    .of(issuer = "https://auth.example.com", audience = "https://api.example.com")
    .withLeeway(30.seconds)

def verified(token: String): IO[Jwt] =
  JwtVerifier.verify[IO](token, keys, rules) // fails with a JwtException; refetches the keys on an unknown key id

def offline(token: String, jwks: Jwks): Either[JwtError, Jwt] =
  JwtVerifier.verify(token, jwks, rules, Instant.now()) // no effect, no network

def read(jwt: Jwt): (Option[String], Vector[String], Option[Instant], Vector[String]) =
  (jwt.claims.subject, jwt.claims.audience, jwt.claims.expiresAt, jwt.claims.raw.strings("roles"))

val stricter: JwtValidation =
  rules
    .withAlgorithms(JwsAlgorithm.ES256, JwsAlgorithm.RS256) // the default is every asymmetric algorithm
    .withCheck(claims => Either.cond(claims.string("tenant").contains("acme"), (), JwtError.InvalidClaim("tenant", "another tenant's token")))

def signed(key: PrivateKey, now: Instant): Either[JwtError, String] =
  val claims = Json.obj("iss" -> Json.str("billing"), "aud" -> Json.str("https://auth.example.com"), "exp" -> Json.num(now.getEpochSecond + 300))

  JwtSigner.sign(claims, JwsAlgorithm.ES256, key, Some("key-1"))

def fromFile(pem: String): Either[JwtError, PrivateKey] =
  JwtSigner.privateKey(pem, "EC") // a PKCS#8 "BEGIN PRIVATE KEY" file; "RSA" for an RSA key
