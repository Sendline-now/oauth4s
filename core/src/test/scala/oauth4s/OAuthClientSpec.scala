package oauth4s

import java.util.Base64
import java.net.ConnectException

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration.FiniteDuration

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.http.HttpResponse
import oauth4s.json.{Json, JsonCodec}
import oauth4s.Fixtures.{Config, Redirect, TokenReply}

import scala.concurrent.duration.given

final class OAuthClientSpec extends AnyWordSpec, Matchers:
  import OAuthClientSpec.*

  "authorize" should {
    "build the redirect with a fresh state and an S256 challenge of the verifier it keeps" in {
      val client     = clientOf(RecordingHttp.routes())
      val flow       = client.authorize(Redirect, AuthorizationOptions.scoped("read", "write")).get
      val parameters = AuthorizationCallback.fromUrl(flow.url)

      (
        flow.url.takeWhile(_ != '?'),
        parameters.parameter("response_type"),
        parameters.parameter("client_id"),
        parameters.parameter("redirect_uri"),
        parameters.parameter("scope"),
        parameters.parameter("state"),
        parameters.parameter("code_challenge_method"),
        parameters.parameter("code_challenge"),
        parameters.parameter("nonce"),
      ) shouldBe (
        "https://auth.example.com/authorize",
        Some("code"),
        Some("client-1"),
        Some("https://app.example.com/callback"),
        Some("read write"),
        Some(flow.state.value),
        Some("S256"),
        flow.verifier.map(_.challenge(PkceMethod.S256).value),
        None,
      )
    }

    "never repeat a state or a verifier, and make the state long enough for the strictest provider" in {
      val client = clientOf(RecordingHttp.routes())
      val flows  = List.fill(20)(client.authorize(Redirect).get)

      (flows.map(_.state).distinct.size, flows.flatMap(_.verifier).distinct.size, flows.forall(_.state.value.length >= 32)) shouldBe (20, 20, true)
    }

    "fall back to the scope of the configuration, in the separator the provider wants, and let a later parameter win" in {
      val config = Config.withScope(Scope.of("profile", "email")).withScopeSeparator(",").withAuthorizationParameter("prompt", "login")
      val flow   = clientOf(RecordingHttp.routes(), config).authorize(Redirect, AuthorizationOptions.default.withoutPkce.withNonce.withPrompt("consent")).get
      val sent   = AuthorizationCallback.fromUrl(flow.url)

      (sent.parameter("scope"), sent.parameter("prompt"), sent.parameter("code_challenge"), flow.verifier, sent.parameter("nonce")) shouldBe (
        Some("profile,email"),
        Some("consent"),
        None,
        None,
        flow.nonce.map(_.value),
      )
    }

    "refuse to start a flow on a client that has no authorization endpoint" in {
      val config = OAuthClientConfig(Fixtures.ClientId, ClientAuthentication.Public, Endpoints.of(Fixtures.Token))

      errorOf(clientOf(RecordingHttp.routes(), config).authorize(Redirect)) shouldBe OAuthError.MissingEndpoint("authorization")
    }
  }

  "complete" should {
    "exchange the code with the verifier of the flow and the credentials in a Basic header" in {
      val http   = RecordingHttp.routes(Fixtures.Token.value -> TokenReply)
      val client = clientOf(http)
      val flow   = client.authorize(Redirect).get
      val token  = client.complete(flow, AuthorizationCallback.fromUrl(s"${Redirect.value}?code=abc&state=${flow.state.value}")).get

      (token.accessToken.value, token.refreshToken.map(_.value), token.scope.map(_.values), token.expiresIn, http.lastForm, http.last.header("Authorization")) shouldBe (
        "access-1",
        Some("refresh-1"),
        Some(Vector("read", "write")),
        Some(1.hour),
        Map(
          "grant_type"    -> "authorization_code",
          "code"          -> "abc",
          "redirect_uri"  -> Redirect.value,
          "code_verifier" -> flow.verifier.get.value,
        ),
        Some(s"Basic ${Base64.getEncoder.encodeToString("client-1:s3cret".getBytes)}"),
      )
    }

    "stop before the token endpoint when the callback carries another state, an error or another issuer" in {
      val http   = RecordingHttp.routes(Fixtures.Token.value -> TokenReply)
      val client = clientOf(http, Config.withIssuer(Fixtures.Issuer))
      val flow   = client.authorize(Redirect).get

      def completed(query: String) = errorOf(client.complete(flow, AuthorizationCallback.fromUrl(s"${Redirect.value}?$query")))

      (
        completed("code=abc&state=forged"),
        completed("code=abc"),
        completed(s"error=access_denied&error_description=no+thanks&state=${flow.state.value}"),
        completed(s"code=abc&state=${flow.state.value}&iss=https%3A%2F%2Fevil.example.com"),
        completed(s"state=${flow.state.value}"),
        http.requests.size,
      ) shouldBe (
        OAuthError.StateMismatch(flow.state, Some(State("forged"))),
        OAuthError.StateMismatch(flow.state, None),
        OAuthError.Rejected(ErrorCode.AccessDenied, Some("no thanks"), None),
        OAuthError.IssuerMismatch(Fixtures.Issuer, "https://evil.example.com"),
        OAuthError.MissingField("code"),
        0,
      )
    }

    "forward the callback parameters a provider wants back, such as VK ID's device_id" in {
      val http   = RecordingHttp.routes(Fixtures.Token.value -> TokenReply)
      val client = clientOf(http, Config.forwardingCallbackParameter("device_id"))
      val flow   = client.authorize(Redirect).get

      client.complete(flow, AuthorizationCallback.fromUrl(s"${Redirect.value}?code=abc&device_id=dev-7&state=${flow.state.value}")).get

      http.lastForm.get("device_id") shouldBe Some("dev-7")
    }
  }

  "the token endpoint" should {
    "receive the credentials in the body for client_secret_post, and only the client id for a public client" in {
      val http    = RecordingHttp.routes(Fixtures.Token.value -> TokenReply)
      val posted  = Fixtures.Endpoints
      val secret  = clientOf(http, OAuthClientConfig(Fixtures.ClientId, ClientAuthentication.SecretPost(Fixtures.ClientSecret), posted))
      val public  = clientOf(http, OAuthClientConfig(Fixtures.ClientId, ClientAuthentication.Public, posted))
      val minted  = clientOf(http, OAuthClientConfig(Fixtures.ClientId, ClientAuthentication.SecretMinted(now => ClientSecret(s"minted-${now.getEpochSecond}")), posted))
      val results = List(secret, public, minted).map { client =>
        client.clientCredentials(Scope.of("read")).get
        (http.lastForm.get("client_id"), http.lastForm.get("client_secret"), http.last.header("Authorization"))
      }

      results shouldBe List(
        (Some("client-1"), Some("s3cret"), None),
        (Some("client-1"), None, None),
        (Some("client-1"), Some(s"minted-${ManualTime.Start.getEpochSecond}"), None),
      )
    }

    "percent-encode a client id and secret before they go into the Basic header" in {
      val http   = RecordingHttp.routes(Fixtures.Token.value -> TokenReply)
      val config = OAuthClientConfig(ClientId("id with space"), ClientAuthentication.SecretBasic(ClientSecret("p@ss:word")), Fixtures.Endpoints)

      clientOf(http, config).clientCredentials().get

      http.last.header("Authorization") shouldBe Some(s"Basic ${Base64.getEncoder.encodeToString("id+with+space:p%40ss%3Aword".getBytes)}")
    }

    "refresh with the refresh token and no scope unless one is asked for" in {
      val http   = RecordingHttp.routes(Fixtures.Token.value -> TokenReply)
      val client = clientOf(http, Config.withScope(Scope.of("default")))

      client.refresh(RefreshToken("refresh-1")).get
      val plain = http.lastForm
      client.refresh(RefreshToken("refresh-1"), Scope.of("read")).get

      (plain, http.lastForm.get("scope")) shouldBe (Map("grant_type" -> "refresh_token", "refresh_token" -> "refresh-1"), Some("read"))
    }

    "turn an RFC 6749 error document into a Rejected value, also when it arrives with HTTP 200" in {
      val rejected = Fixtures.rejected("invalid_grant", "the grant expired")
      val asOk     = Fixtures.rejected("invalid_request", "missing device_id", status = 200)

      (
        errorOf(clientOf(RecordingHttp.sequence(rejected)).refresh(RefreshToken("old"))),
        errorOf(clientOf(RecordingHttp.sequence(asOk)).refresh(RefreshToken("old"))),
      ) shouldBe (
        OAuthError.Rejected(ErrorCode.InvalidGrant, Some("the grant expired"), None),
        OAuthError.Rejected(ErrorCode.InvalidRequest, Some("missing device_id"), None),
      )
    }

    "read the error shapes of providers that do not follow the RFC, and keep the body of anything else" in {
      val facebook  = HttpResponse.json("""{"error":{"message":"Invalid verification code format.","type":"OAuthException","code":100}}""", 400)
      val instagram = HttpResponse.json("""{"error_type":"OAuthException","code":400,"error_message":"Invalid platform app"}""", 400)
      val gateway   = HttpResponse.of(502, "<html>Bad Gateway</html>")

      List(facebook, instagram, gateway).map(reply => errorOf(clientOf(RecordingHttp.sequence(reply)).clientCredentials())) shouldBe List(
        OAuthError.Rejected(ErrorCode.Other("OAuthException"), Some("Invalid verification code format."), None),
        OAuthError.Rejected(ErrorCode.Other("OAuthException"), Some("Invalid platform app"), None),
        OAuthError.UnexpectedStatus(502, "<html>Bad Gateway</html>"),
      )
    }

    "report a token response without an access token, or one that is not JSON, instead of an empty token" in {
      (
        errorOf(clientOf(RecordingHttp.sequence(HttpResponse.json("""{"token_type":"Bearer"}"""))).clientCredentials()),
        errorOf(clientOf(RecordingHttp.sequence(HttpResponse.json("""{"access_token":"a","expires_in":"soon"}"""))).clientCredentials()),
        errorOf(clientOf(RecordingHttp.sequence(HttpResponse.of(200, "not json"))).clientCredentials()).message.take(26),
      ) shouldBe (
        OAuthError.MissingField("access_token"),
        OAuthError.MalformedResponse("'expires_in' is not a non-negative number of seconds"),
        "the response is malformed:",
      )
    }

    "default a missing token_type to Bearer and accept expires_in as a string" in {
      val token = clientOf(RecordingHttp.sequence(HttpResponse.json("""{"access_token":"a","expires_in":"120"}"""))).clientCredentials().get

      (token.tokenType, token.expiresIn, token.expiresAt) shouldBe (TokenType.Bearer, Some(2.minutes), Some(ManualTime.Start.plusSeconds(120)))
    }

    "let a provider reshape its token response before it is read" in {
      val wrapped = HttpResponse.json("""{"data":[{"access_token":"ig-1","user_id":"42"}]}""")
      val config  = Config.adaptingTokenResponse(json => json.array("data").flatMap(_.headOption).getOrElse(json))
      val token   = clientOf(RecordingHttp.sequence(wrapped), config).clientCredentials().get

      (token.accessToken.value, token.raw.string("user_id")) shouldBe ("ig-1", Some("42"))
    }

    "surface a transport failure with its cause" in {
      val cause = ConnectException("connection refused")
      val error = clientOf(RecordingHttp.failing(cause)).clientCredentials().failed.get.asInstanceOf[OAuthException]

      (error.error, error.getCause) shouldBe (OAuthError.Transport(cause), cause)
    }

    "not print tokens or secrets from toString" in {
      val token = clientOf(RecordingHttp.sequence(TokenReply)).clientCredentials().get

      (token.toString.contains("access-1"), token.toString.contains("refresh-1"), Config.toString.contains("s3cret"), Config.authentication.toString) shouldBe (
        false,
        false,
        false,
        "ClientAuthentication(client_secret_basic)",
      )
    }
  }

  "the device flow" should {
    "poll at the server's interval, slow down by five seconds when told to, and stop at the first token" in {
      val time   = ManualTime()
      val device =
        HttpResponse.json("""{"device_code":"dev-1","user_code":"WDJB-MJHT","verification_uri":"https://auth.example.com/activate","expires_in":600,"interval":5}""")
      val http   =
        RecordingHttp.sequence(device, Fixtures.rejected("authorization_pending"), Fixtures.rejected("slow_down"), Fixtures.rejected("authorization_pending"), TokenReply)
      val client = clientOf(http, time = time)
      val grant  = client.deviceAuthorization(Scope.of("read")).get
      val token  = client.awaitDevice(grant).get

      (grant.userCode.value, grant.verificationUri, token.accessToken.value, time.sleeps, http.lastForm) shouldBe (
        "WDJB-MJHT",
        "https://auth.example.com/activate",
        "access-1",
        Vector[FiniteDuration](5.seconds, 10.seconds, 10.seconds),
        Map("grant_type" -> "urn:ietf:params:oauth:grant-type:device_code", "device_code" -> "dev-1"),
      )
    }

    "give up with expired_token once the device code has run out, without waiting past it" in {
      val time    = ManualTime()
      val device  = HttpResponse.json("""{"device_code":"dev-1","user_code":"A","verification_uri":"https://auth.example.com/activate","expires_in":12,"interval":5}""")
      val pending = List.fill(5)(Fixtures.rejected("authorization_pending"))
      val client  = clientOf(RecordingHttp.sequence((device :: pending)*), time = time)
      val error   = errorOf(client.awaitDevice(client.deviceAuthorization().get))

      (error, time.sleeps) shouldBe (
        OAuthError.Rejected(ErrorCode.ExpiredToken, Some("the device code expired before the user approved it"), None),
        Vector[FiniteDuration](5.seconds, 5.seconds),
      )
    }
  }

  "revocation, introspection and resources" should {
    "revoke with the token type hint and succeed on any 2xx" in {
      val http   = RecordingHttp.routes(Fixtures.Revocation.value -> HttpResponse.of(200, ""))
      val client = clientOf(http)

      client.revokeRefreshToken(RefreshToken("refresh-1")).get
      val refresh = http.lastForm
      client.revokeAccessToken(AccessToken("access-1")).get

      (refresh, http.lastForm) shouldBe (
        Map("token" -> "refresh-1", "token_type_hint" -> "refresh_token"),
        Map("token" -> "access-1", "token_type_hint"  -> "access_token"),
      )
    }

    "read an introspection response, with an audience that is one string or many" in {
      val active = """{"active":true,"scope":"read","client_id":"client-1","sub":"user-1","aud":"api","exp":1767229200}"""
      val result = clientOf(RecordingHttp.routes(Fixtures.Introspection.value -> HttpResponse.json(active))).introspect(AccessToken("access-1")).get

      (result.active, result.scope.map(_.render), result.clientId, result.subject, result.audience, result.expiresAt.map(_.getEpochSecond)) shouldBe (
        true,
        Some("read"),
        Some(Fixtures.ClientId),
        Some("user-1"),
        Vector("api"),
        Some(1767229200L),
      )
    }

    "fetch a protected resource with the access token as a bearer" in {
      val http   = RecordingHttp.routes("https://api.example.com/me" -> HttpResponse.json("""{"id":"7"}"""))
      val result = clientOf(http).resource(Endpoint("https://api.example.com/me"), AccessToken("access-1"), Seq("fields" -> "id,name")).get

      (result, http.last.header("Authorization"), http.lastQuery) shouldBe (Json.obj("id" -> Json.str("7")), Some("Bearer access-1"), Map("fields" -> "id,name"))
    }
  }

object OAuthClientSpec:

  def clientOf(http: RecordingHttp, config: OAuthClientConfig = Config, time: ManualTime = ManualTime()): OAuthClient[Try] =
    OAuthClient[Try](config)(using time, http, JsonCodec.native)

  def errorOf[A](result: Try[A]): OAuthError =
    result match
      case Failure(exception: OAuthException) => exception.error
      case Failure(other)                     => throw other
      case Success(value)                     => throw AssertionError(s"expected an OAuthError, got $value")
