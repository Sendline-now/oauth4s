package oauth4s.jwt.internal

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration.FiniteDuration

import oauth4s.json.JsonCodec
import oauth4s.jwt.{Jwks, JwksSource}
import oauth4s.internal.{Requests, Responses}
import oauth4s.http.{HttpClient, HttpHeaders}
import oauth4s.{Effect, Endpoint, OAuthError}

private[jwt] final class RemoteJwksSource[F[*]](
    endpoint: Endpoint,
    lifetime: FiniteDuration,
    cooldown: FiniteDuration,
)(using F: Effect[F], http: HttpClient[F], codec: JsonCodec)
    extends JwksSource[F]:

  private val cached = AtomicReference[Option[(Jwks, Instant)]](None)

  def keys: F[Jwks] = within(lifetime)

  def refreshed: F[Jwks] = within(cooldown)

  private def within(age: FiniteDuration): F[Jwks] =
    F.flatMap(F.realTime) { now =>
      cached.get match
        case Some((jwks, fetched)) if now.isBefore(fetched.plusMillis(age.toMillis)) => F.pure(jwks)
        case _                                                                       => fetch(now)
    }

  private def fetch(now: Instant): F[Jwks] =
    F.flatMap(Requests.get(endpoint.value, Nil, HttpHeaders.empty)) { response =>
      val decoded = Responses.decode(response)(json => Jwks.fromJson(json).left.map(error => OAuthError.MalformedResponse(error.message)))

      F.map(Requests.lift(decoded)) { jwks =>
        cached.set(Some((jwks, now)))
        jwks
      }
    }
