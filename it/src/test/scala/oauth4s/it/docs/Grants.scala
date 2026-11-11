package oauth4s.it.docs.grants

import cats.effect.IO

import oauth4s.*

import oauth4s.runtime.cats.CatsInstances.given

final class Grants(client: OAuthClient[IO], refreshToken: RefreshToken, accessToken: AccessToken):
  val machine: IO[TokenResponse]   = client.clientCredentials(Scope.of("read")) // RFC 6749 §4.4
  val renewed: IO[TokenResponse]   = client.refresh(refreshToken)               // RFC 6749 §6
  val revoked: IO[Unit]            = client.revokeRefreshToken(refreshToken)    // RFC 7009
  val inspected: IO[Introspection] = client.introspect(accessToken)             // RFC 7662

  val exchanged: IO[TokenResponse] = // RFC 8693, or any grant the client has no method for
    client.requestToken(
      GrantType.TokenExchange,
      Seq("subject_token" -> accessToken.value, "subject_token_type" -> "urn:ietf:params:oauth:token-type:access_token"),
    )

  val onDevice: IO[TokenResponse] =
    for
      device <- client.deviceAuthorization(Scope.of("read")) // RFC 8628
      _      <- IO.println(s"enter ${device.userCode.value} at ${device.verificationUri}")
      token  <- client.awaitDevice(device)                   // polls at the server's interval, backs off on slow_down
    yield token

final class Tokens(client: OAuthClient[IO], stored: TokenResponse):
  val service: TokenSource[IO] = TokenSource.clientCredentials(client, Scope.of("read"))
  val user: TokenSource[IO]    = TokenSource.refreshing(client, stored)

  def call[A](request: AccessToken => IO[A]): IO[A] =
    service.accessToken.flatMap(request) // asks once, then serves from memory until a minute before the expiry

  val refused: IO[Unit] = service.invalidate // the resource server answered 401: the next call asks again

  def active(token: AccessToken): IO[Boolean] =
    client.introspect(token).map(answer => answer.active && answer.scope.exists(_.contains("read")))
