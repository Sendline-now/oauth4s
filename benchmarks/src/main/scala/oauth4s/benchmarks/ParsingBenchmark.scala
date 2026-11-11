package oauth4s.benchmarks

import java.time.Instant
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.*

import oauth4s.testkit.TestIssuer
import oauth4s.{ClientId, TokenResponse}
import oauth4s.json.circe.CirceInstances
import oauth4s.json.{Json, JsonCodec, JsonError}
import oauth4s.jwt.{Jwks, Jwt, JwtError, JwtValidation, JwtVerifier}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
class ParsingBenchmark:
  private val now      = Instant.parse("2026-01-01T00:00:00Z")
  private val identity = TestIssuer()
  private val keys     = identity.jwks
  private val idToken  = identity.idToken(ClientId("client-1"), "user-1", now).value
  private val checks   = JwtValidation.of(identity.issuer.value, "client-1")
  private val response = s"""{"access_token":"access-1","token_type":"Bearer","expires_in":3600,"refresh_token":"refresh-1","scope":"read write","id_token":"$idToken"}"""

  @Benchmark
  def tokenResponseNative: Either[JsonError, Json] = JsonCodec.native.parse(response)

  @Benchmark
  def tokenResponseCirce: Either[JsonError, Json] = CirceInstances.circeCodec.parse(response)

  @Benchmark
  def tokenResponseDecoded: Option[TokenResponse] = JsonCodec.native.parse(response).toOption.flatMap(TokenResponse.fromJson(_, now).toOption)

  @Benchmark
  def idTokenParsed: Either[JwtError, Jwt] = Jwt.parse(idToken)

  @Benchmark
  def idTokenVerified: Either[JwtError, Jwt] = JwtVerifier.verify(idToken, keys, checks, now)

  @Benchmark
  def keySetParsed: Either[JwtError, Jwks] = Jwks.fromJson(identity.jwksJson)
