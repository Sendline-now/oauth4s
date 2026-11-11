package oauth4s.it

import cats.effect.IO
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.*
import oauth4s.http.HttpClient
import oauth4s.oidc.OidcClient
import oauth4s.http.jdk.JdkHttpClient
import oauth4s.jwt.{JwtError, JwtException}
import oauth4s.testkit.FakeAuthorizationServer

import cats.effect.unsafe.implicits.global
import oauth4s.runtime.cats.CatsInstances.given

final class OidcFlowItSpec extends AnyWordSpec, Matchers, BeforeAndAfterAll:
  private val server   = FakeAuthorizationServer.start()
  private val redirect = RedirectUri("http://127.0.0.1/callback")

  private given HttpClient[IO] = JdkHttpClient[IO]()

  override protected def afterAll(): Unit = server.close()

  private def discovered: IO[OidcClient[IO]] =
    OidcClient.discover[IO](server.issuer, server.clientId, ClientAuthentication.SecretBasic(server.clientSecret))

  "an OpenID Connect client over real HTTP" should {
    "discover the provider, sign a user in, verify the ID token against the published keys and read the user" in {
      val program =
        for
          client <- discovered
          flow   <- client.authorize(redirect, AuthorizationOptions.scoped("email"))
          tokens <- client.complete(flow, AuthorizationCallback.fromUrl(server.approve(flow.url)))
          info   <- client.userInfo(tokens.response.accessToken)
          again  <- client.verify(tokens.response.idToken.get, flow.nonce)
        yield (
          tokens.user.subject,
          tokens.idToken.claims.nonce == flow.nonce.map(_.value),
          info.email,
          info.emailVerified,
          again.claims.subject,
          tokens.response.scope.map(_.render)
        )

      program.unsafeRunSync() shouldBe (Some("user-1"), true, Some("user@example.com"), Some(true), Some("user-1"), Some("openid email"))
    }

    "refuse an ID token replayed into another flow, whose nonce it does not carry" in {
      val program =
        for
          client <- discovered
          first  <- client.authorize(redirect)
          second <- client.authorize(redirect)
          tokens <- client.complete(first, AuthorizationCallback.fromUrl(server.approve(first.url)))
          replay <- client.verify(tokens.response.idToken.get, second.nonce).attempt
        yield replay.left.map { case error: JwtException => error.error }.map(_.claims.subject)

      program.unsafeRunSync() shouldBe Left(JwtError.NonceMismatch)
    }
  }
