package oauth4s.jwt

import scala.concurrent.duration.{FiniteDuration, HOURS, MINUTES}

import oauth4s.json.JsonCodec
import oauth4s.http.HttpClient
import oauth4s.{Effect, Endpoint}
import oauth4s.jwt.internal.RemoteJwksSource

trait JwksSource[F[*]]:
  def keys: F[Jwks]
  def refreshed: F[Jwks]

object JwksSource:
  val DefaultLifetime: FiniteDuration = FiniteDuration(1, HOURS)
  val DefaultCooldown: FiniteDuration = FiniteDuration(1, MINUTES)

  def fixed[F[*]](jwks: Jwks)(using F: Effect[F]): JwksSource[F] = new JwksSource[F]:
    def keys: F[Jwks]      = F.pure(jwks)
    def refreshed: F[Jwks] = F.pure(jwks)

  def remote[F[*]](endpoint: Endpoint, lifetime: FiniteDuration = DefaultLifetime, cooldown: FiniteDuration = DefaultCooldown)(using
      Effect[F],
      HttpClient[F],
      JsonCodec,
  ): JwksSource[F] = RemoteJwksSource(endpoint, lifetime, cooldown)
