package oauth4s.it

import java.time.Instant

import cats.effect.{IO, Ref}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.*
import oauth4s.json.Json
import oauth4s.oidc.OidcClient
import oauth4s.providers.Provider
import oauth4s.http.jdk.JdkHttpClient
import oauth4s.http.{HttpClient, HttpResponse}
import oauth4s.jwt.{JwksSource, JwtValidation}
import oauth4s.it.docs.recipes.guard.Guard
import oauth4s.it.docs.recipes.signin.SignIn
import oauth4s.it.docs.recipes.fresh.withToken
import oauth4s.it.docs.recipes.sessions.Sessions
import oauth4s.it.docs.testing.{EndToEnd, Scripted}
import oauth4s.testkit.{FakeAuthorizationServer, StubHttpClient, TestIssuer}

import cats.effect.unsafe.implicits.global
import oauth4s.runtime.cats.CatsInstances.given

final class RecipesItSpec extends AnyWordSpec, Matchers, BeforeAndAfterAll:
  private val server   = FakeAuthorizationServer.start()
  private val redirect = RedirectUri("http://127.0.0.1/callback")

  private given HttpClient[IO] = JdkHttpClient[IO]()

  override protected def afterAll(): Unit = server.close()

  private def oauth: OAuthClient[IO] =
    OAuthClient[IO](OAuthClientConfig(server.clientId, ClientAuthentication.SecretBasic(server.clientSecret), server.endpoints))

  "the testing examples" should {
    "show the form a scripted client was asked with, and sign a user in against the fake server" in {
      (Scripted.asked.unsafeRunSync(), EndToEnd(server).signIn.unsafeRunSync().user.subject) shouldBe (
        Map("grant_type" -> "client_credentials", "scope" -> "read", "client_id" -> "client-1"),
        Some("user-1"),
      )
    }
  }

  "the sign-in recipe" should {
    "finish the flow a callback belongs to, once, and refuse a callback nothing is waiting for" in {
      val program =
        for
          oidc    <- OidcClient.discover[IO](server.issuer, server.clientId, ClientAuthentication.SecretBasic(server.clientSecret))
          signIn  <- SignIn.of(Provider.oidc("example", oidc), redirect)
          url     <- signIn.begin
          landed   = AuthorizationCallback.fromUrl(server.approve(url))
          login   <- signIn.finish(landed)
          replay  <- signIn.finish(landed).attempt
          unknown <- signIn.finish(AuthorizationCallback.fromUrl(s"${redirect.value}?code=abc&state=forged")).attempt
        yield (login.profile.provider, login.profile.id, login.profile.email, replay.left.map(_.getMessage), unknown.isLeft)

      program.unsafeRunSync() shouldBe (
        "example",
        "user-1",
        Some("user@example.com"),
        Left("no sign-in is waiting for this callback"),
        true,
      )
    }
  }

  "the fresh token recipe" should {
    "ask for a new token and try once more when the API answers 401, and give up on anything else" in {
      val program =
        for
          calls  <- Ref.of[IO, List[String]](Nil)
          tokens  = TokenSource.clientCredentials(oauth, Scope.of("read"))
          answer <- withToken(tokens)(token => calls.modify(seen => (seen :+ token.value, if seen.isEmpty then Left(401) else Right(seen.size))))
          seen   <- calls.get
          failed <- withToken(tokens)(_ => IO.pure(Left(503): Either[Int, Int])).attempt
        yield (answer, seen.size, seen.distinct.size, failed.left.map(_.getMessage))

      program.unsafeRunSync() shouldBe (1, 2, 2, Left("the API answered HTTP 503"))
    }
  }

  "the guard recipe" should {
    "let a token through that is signed by the issuer and granted the scope, and say why another is refused" in {
      val issuer = TestIssuer(Issuer("https://auth.example.com"))
      val stub   = StubHttpClient.routes[IO]("https://auth.example.com/jwks" -> HttpResponse.json(issuer.jwksJson.render))
      val guard  = Guard(
        JwksSource.remote[IO](Endpoint("https://auth.example.com/jwks"))(using summon, stub),
        JwtValidation.of("https://auth.example.com", "https://api.example.com")
      )
      val token  = issuer.idToken(ClientId("https://api.example.com"), "user-1", Instant.now(), "scope" -> Json.str("invoices:read")).value
      val other  = TestIssuer(Issuer("https://auth.example.com")).idToken(ClientId("https://api.example.com"), "user-1", Instant.now()).value

      val program =
        for
          allowed <- guard.allow(Some(s"Bearer $token"), "invoices:read")
          narrow  <- guard.allow(Some(s"Bearer $token"), "invoices:write")
          forged  <- guard.allow(Some(s"Bearer $other"), "invoices:read")
          missing <- guard.allow(None, "invoices:read")
        yield (allowed.map(_.claims.subject), narrow, forged, missing)

      program.unsafeRunSync() shouldBe (
        Right(Some("user-1")),
        Left("the token was not granted invoices:write"),
        Left("the signature does not verify"),
        Left("the request carries no bearer token"),
      )
    }
  }

  "the sessions recipe" should {
    "serve the stored token while it is valid, and store the refreshed one when it is not" in {
      val program =
        for
          store   <- Ref.of[IO, Map[String, String]](Map.empty)
          sessions = Sessions(oauth, user => store.get.map(_.get(user)), (user, text) => store.update(_ + (user -> text)))
          flow    <- oauth.authorize(redirect, AuthorizationOptions.scoped("read"))
          tokens  <- oauth.complete(flow, AuthorizationCallback.fromUrl(server.approve(flow.url)))
          _       <- sessions.signedIn("ann", tokens)
          same    <- sessions.accessToken("ann")
          _       <- sessions.signedIn("ann", tokens.copy(issuedAt = tokens.issuedAt.minusSeconds(7200)))
          renewed <- sessions.accessToken("ann")
          again   <- sessions.accessToken("ann")
          nobody  <- sessions.accessToken("bob")
        yield (same.contains(tokens.accessToken), renewed.exists(_ != tokens.accessToken), again == renewed, nobody)

      program.unsafeRunSync() shouldBe (true, true, true, None)
    }
  }
