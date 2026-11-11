package oauth4s.testkit

import java.net.ConnectException
import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.*
import oauth4s.json.JsonCodec
import oauth4s.http.HttpResponse

import scala.concurrent.duration.given

abstract class RuntimeSuite[F[*]](runtime: String)(using F: Effect[F]) extends AnyWordSpec, Matchers:
  import RuntimeSuite.*

  protected def unsafeRun[A](fa: F[A]): A

  private final class Boom(message: String) extends RuntimeException(message)

  private def run[A](fa: F[A]): Either[Throwable, A] = unsafeRun(F.attempt(fa))

  private def clientOf(http: StubHttpClient[F]): OAuthClient[F] = OAuthClient[F](Config)(using F, http, JsonCodec.native)

  s"Effect[$runtime]" should {
    "suspend side effects until the effect is run" in {
      val calls     = AtomicInteger(0)
      val delayed   = F.delay(calls.incrementAndGet())
      val suspended = F.suspend(F.pure(calls.incrementAndGet()))
      val blocking  = F.blocking(calls.incrementAndGet())
      val before    = calls.get()

      (before, unsafeRun(F.flatMap(delayed)(_ => F.flatMap(suspended)(_ => blocking)))) shouldBe (0, 3)
    }

    "raise, recover and attempt errors, including the ones thrown inside delay, map and flatMap" in {
      val boom = Boom("boom")

      (
        run(F.raiseError[Int](boom)),
        unsafeRun(F.handleErrorWith(F.raiseError[Int](boom))(_ => F.pure(1))),
        run(Effect.fromEither[F, Int](Right(2))),
        run(F.delay[Int](throw boom)),
        run(F.map[Int, Int](F.pure(1))(_ => throw boom)),
        run(F.flatMap[Int, Int](F.pure(1))(_ => throw boom)),
        run(F.suspend[Int](throw boom)),
      ) shouldBe (Left(boom), 1, Right(2), Left(boom), Left(boom), Left(boom), Left(boom))
    }

    "sleep for at least as long as it is asked, by a clock that reads the present" in {
      val elapsed = unsafeRun(F.flatMap(F.realTime)(start => F.flatMap(F.sleep(30.millis))(_ => F.map(F.realTime)(end => end.toEpochMilli - start.toEpochMilli))))
      val drift   = math.abs(unsafeRun(F.realTime).toEpochMilli - System.currentTimeMillis())

      (elapsed >= 25, drift < 5000) shouldBe (true, true)
    }
  }

  s"an OAuth client on $runtime" should {
    "exchange client credentials for a token, authenticating in a Basic header" in {
      val http  = StubHttpClient.routes[F](Token.value -> TokenReply)
      val token = unsafeRun(clientOf(http).clientCredentials(Scope.of("read")))

      (token.accessToken.value, token.expiresIn, http.lastForm, http.lastRequest.flatMap(_.header("Authorization")).map(_.take(6))) shouldBe (
        "access-1",
        Some(1.hour),
        Map("grant_type" -> "client_credentials", "scope" -> "read"),
        Some("Basic "),
      )
    }

    "complete an authorization code flow with the PKCE verifier it generated" in {
      val http   = StubHttpClient.routes[F](Token.value -> TokenReply)
      val client = clientOf(http)
      val flow   = unsafeRun(client.authorize(Redirect))
      val token  = unsafeRun(client.complete(flow, AuthorizationCallback.fromUrl(s"${Redirect.value}?code=abc&state=${flow.state.value}")))

      (token.refreshToken.map(_.value), http.lastForm.get("code_verifier"), flow.url.contains("code_challenge_method=S256")) shouldBe (
        Some("refresh-1"),
        flow.verifier.map(_.value),
        true,
      )
    }

    "raise a rejected grant and a transport failure as an OAuthException that carries the value" in {
      val cause    = ConnectException("connection refused")
      val rejected = StubHttpClient.routes[F](Token.value -> HttpResponse.json("""{"error":"invalid_grant"}""", 400))

      (
        run(clientOf(rejected).refresh(RefreshToken("old"))).left.map { case error: OAuthException => error.error },
        run(clientOf(StubHttpClient.failing[F](cause)).clientCredentials()).left.map { case error: OAuthException => (error.error, error.getCause) },
      ) shouldBe (
        Left(OAuthError.Rejected(ErrorCode.InvalidGrant, None, None)),
        Left((OAuthError.Transport(cause), cause)),
      )
    }

    "poll the device flow until the user approves, sleeping on the runtime between attempts" in {
      val device = HttpResponse.json("""{"device_code":"dev-1","user_code":"A","verification_uri":"https://auth.example.com/activate","expires_in":60,"interval":0.02}""")
      val http   = StubHttpClient
        .sequence[F](device, HttpResponse.json("""{"error":"authorization_pending"}""", 400), HttpResponse.json("""{"error":"authorization_pending"}""", 400), TokenReply)
      val client = clientOf(http)
      val token  = unsafeRun(F.flatMap(client.deviceAuthorization())(client.awaitDevice))

      (token.accessToken.value, http.requests.size) shouldBe ("access-1", 4)
    }

    "serve a cached token from a token source without asking again" in {
      val http   = StubHttpClient.routes[F](Token.value -> TokenReply)
      val source = TokenSource.clientCredentials(clientOf(http))
      val tokens = unsafeRun(F.flatMap(source.accessToken)(first => F.map(source.accessToken)(second => (first.value, second.value))))

      (tokens, http.requests.size) shouldBe (("access-1", "access-1"), 1)
    }
  }

object RuntimeSuite:
  val Token: Endpoint       = Endpoint("https://auth.example.com/token")
  val Redirect: RedirectUri = RedirectUri("https://app.example.com/callback")

  val Config: OAuthClientConfig =
    val endpoints = Endpoints.of(Endpoint("https://auth.example.com/authorize"), Token).withDeviceAuthorization(Endpoint("https://auth.example.com/device"))

    OAuthClientConfig(ClientId("client-1"), ClientAuthentication.SecretBasic(ClientSecret("s3cret")), endpoints)

  val TokenReply: HttpResponse =
    HttpResponse.json("""{"access_token":"access-1","token_type":"Bearer","expires_in":3600,"refresh_token":"refresh-1","scope":"read write"}""")
