package oauth4s.testkit

import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.*
import oauth4s.json.JsonCodec
import oauth4s.http.HttpClient

abstract class HttpClientSuite[F[*]](library: String)(using F: Effect[F]) extends AnyWordSpec, Matchers, BeforeAndAfterAll:
  protected def client: HttpClient[F]
  protected def unsafeRun[A](fa: F[A]): A

  private val server   = FakeAuthorizationServer.start()
  private val redirect = RedirectUri("http://127.0.0.1/callback")

  override protected def afterAll(): Unit = server.close()

  private def run[A](fa: F[A]): Either[OAuthError, A] =
    unsafeRun(F.attempt(fa)).left.map {
      case exception: OAuthException => exception.error
      case other                     => throw other
    }

  private def oauth(authentication: ClientAuthentication): OAuthClient[F] =
    OAuthClient[F](OAuthClientConfig(server.clientId, authentication, server.endpoints).withIssuer(server.issuer))(using F, client, JsonCodec.native)

  s"an OAuth client over $library" should {
    "discover a server, run the code flow with PKCE against it, refresh, introspect and revoke" in {
      val basic      = oauth(ClientAuthentication.SecretBasic(server.clientSecret))
      val discovered = unsafeRun(Discovery.discover[F](server.issuer)(using F, client, JsonCodec.native))
      val flow       = unsafeRun(basic.authorize(redirect, AuthorizationOptions.scoped("read", "write")))
      val token      = unsafeRun(basic.complete(flow, AuthorizationCallback.fromUrl(server.approve(flow.url))))
      val refreshed  = unsafeRun(basic.refresh(token.refreshToken.get))
      val active     = unsafeRun(basic.introspect(refreshed.accessToken))
      val reused     = run(basic.refresh(token.refreshToken.get))
      val revoked    = unsafeRun(F.flatMap(basic.revokeAccessToken(refreshed.accessToken))(_ => basic.introspect(refreshed.accessToken)))

      (
        discovered.tokenEndpoint.map(_.value),
        token.scope.map(_.render),
        token.accessToken == refreshed.accessToken,
        (active.active, active.scope.map(_.render)),
        reused,
        revoked.active,
      ) shouldBe (
        Some(server.endpoints.token.value),
        Some("read write"),
        false,
        (true, Some("read write")),
        Left(OAuthError.Rejected(ErrorCode.InvalidGrant, Some("the refresh token is unknown or was rotated"), None)),
        false,
      )
    }

    "authenticate in the form body as well, and be told when the secret is wrong or the verifier is not the flow's" in {
      val posted   = oauth(ClientAuthentication.SecretPost(server.clientSecret))
      val wrong    = oauth(ClientAuthentication.SecretPost(ClientSecret("guess")))
      val flow     = unsafeRun(posted.authorize(redirect))
      val callback = AuthorizationCallback.fromUrl(server.approve(flow.url))
      val stolen   = flow.copy(verifier = Some(unsafeRun(CodeVerifier.generate[F])))

      (
        unsafeRun(posted.clientCredentials(Scope.of("read"))).scope.map(_.render),
        run(wrong.clientCredentials()),
        run(posted.complete(stolen, callback)),
      ) shouldBe (
        Some("read"),
        Left(OAuthError.Rejected(ErrorCode.InvalidClient, Some("the client credentials are wrong"), None)),
        Left(OAuthError.Rejected(ErrorCode.InvalidGrant, Some("the code_verifier does not match the code_challenge"), None)),
      )
    }

    "wait for a device to be approved" in {
      val basic         = oauth(ClientAuthentication.SecretBasic(server.clientSecret))
      val authorization = unsafeRun(basic.deviceAuthorization(Scope.of("read")))

      server.approveDevice(authorization.userCode)

      unsafeRun(basic.awaitDevice(authorization)).scope.map(_.render) shouldBe Some("read")
    }

    "report a server that cannot be reached as a transport failure" in {
      val config = OAuthClientConfig(server.clientId, ClientAuthentication.Public, Endpoints.of(Endpoint("http://127.0.0.1:1/token")))
      val result = run(OAuthClient[F](config)(using F, client, JsonCodec.native).clientCredentials())

      result.left.map(_.message.take(28)) shouldBe Left("the request did not complete")
    }
  }
