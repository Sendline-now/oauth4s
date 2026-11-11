package oauth4s.internal

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration.FiniteDuration

import oauth4s.{AccessToken, Effect, TokenResponse, TokenSource}

private[oauth4s] final class CachedTokenSource[F[*]](
    initial: Option[TokenResponse],
    leeway: FiniteDuration,
)(obtain: Option[TokenResponse] => F[TokenResponse])(using F: Effect[F])
    extends TokenSource[F]:

  private val cached = AtomicReference[CachedTokenSource.State](CachedTokenSource.State(initial, stale = false))

  def token: F[TokenResponse] =
    F.flatMap(F.realTime) { now =>
      val state = cached.get

      state.last.filter(held => !state.stale && !held.expiresWithin(leeway, now)) match
        case Some(fresh) => F.pure(fresh)
        case None        =>
          F.map(obtain(state.last)) { obtained =>
            val kept = obtained.copy(refreshToken = obtained.refreshToken.orElse(state.last.flatMap(_.refreshToken)))

            cached.set(CachedTokenSource.State(Some(kept), stale = false))
            kept
          }
    }

  def accessToken: F[AccessToken] = F.map(token)(_.accessToken)

  def invalidate: F[Unit] = F.delay(cached.updateAndGet(_.copy(stale = true)): Unit)

private[oauth4s] object CachedTokenSource:
  final case class State(last: Option[TokenResponse], stale: Boolean)
