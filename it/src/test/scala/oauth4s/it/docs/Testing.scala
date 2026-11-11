package oauth4s.it.docs.testing

import java.time.Instant

import cats.effect.IO

import oauth4s.*
import oauth4s.json.Json
import oauth4s.oidc.{OidcClient, OidcTokens}
import oauth4s.http.{HttpClient, HttpResponse}
import oauth4s.testkit.{FakeAuthorizationServer, StubHttpClient, TestIssuer}

import oauth4s.runtime.cats.CatsInstances.given

object Scripted:
  val http: StubHttpClient[IO] =
    StubHttpClient.routes[IO]("https://auth.example.com/token" -> HttpResponse.json("""{"access_token":"a","expires_in":3600}"""))

  given HttpClient[IO] = http

  val config = OAuthClientConfig(ClientId("client-1"), ClientAuthentication.Public, Endpoints.of(Endpoint("https://auth.example.com/token")))
  val client = OAuthClient[IO](config)

  val asked: IO[Map[String, String]] =
    client.clientCredentials(Scope.of("read")).map(_ => http.lastForm) // grant_type, scope and client_id, as they were sent

  val failing: StubHttpClient[IO]  = StubHttpClient.failing[IO](java.net.ConnectException("connection refused"))
  val inOrder: StubHttpClient[IO]  = StubHttpClient.sequence[IO](HttpResponse.json("""{"error":"slow_down"}""", 400), HttpResponse.json("""{"access_token":"a"}"""))
  val computed: StubHttpClient[IO] = StubHttpClient[IO](request => IO.pure(HttpResponse.json(s"""{"asked":"${request.url}"}""")))

object Signed:
  val issuer: TestIssuer = TestIssuer(Issuer("https://auth.example.com"))

  val idToken: IdToken = issuer.idToken(ClientId("client-1"), "user-1", Instant.now(), "email" -> Json.str("ann@example.com"))
  val keys: String     = issuer.jwksJson.render // what the provider's jwks_uri would answer

final class EndToEnd(server: FakeAuthorizationServer)(using HttpClient[IO]):
  private val redirect = RedirectUri("http://127.0.0.1/callback")

  val signIn: IO[OidcTokens] =
    for
      oidc   <- OidcClient.discover[IO](server.issuer, server.clientId, ClientAuthentication.SecretBasic(server.clientSecret))
      flow   <- oidc.authorize(redirect, AuthorizationOptions.scoped("email"))
      landed  = server.approve(flow.url) // the user saying yes: the URL the browser would come back to
      tokens <- oidc.complete(flow, AuthorizationCallback.fromUrl(landed))
    yield tokens
