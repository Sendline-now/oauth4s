package oauth4s

import scala.concurrent.duration.{FiniteDuration, SECONDS}

import oauth4s.internal.{CachedTokenSource, Requests}

trait TokenSource[F[*]]:
  def token: F[TokenResponse]
  def accessToken: F[AccessToken]
  def invalidate: F[Unit]

object TokenSource:
  val DefaultLeeway: FiniteDuration = FiniteDuration(60, SECONDS)

  def clientCredentials[F[*]](client: OAuthClient[F], scope: Scope = Scope.empty, leeway: FiniteDuration = DefaultLeeway)(using Effect[F]): TokenSource[F] =
    CachedTokenSource(None, leeway)(_ => client.clientCredentials(scope))

  def refreshing[F[*]](client: OAuthClient[F], initial: TokenResponse, leeway: FiniteDuration = DefaultLeeway)(using Effect[F]): TokenSource[F] =
    CachedTokenSource(Some(initial), leeway) { last =>
      last.orElse(Some(initial)).flatMap(_.refreshToken) match
        case None        => Requests.fail(OAuthError.MissingField("refresh_token"))
        case Some(token) => client.refresh(token)
    }
