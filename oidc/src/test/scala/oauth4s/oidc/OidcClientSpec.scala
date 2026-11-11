package oauth4s.oidc

import java.util.concurrent.atomic.AtomicReference

import scala.util.{Failure, Try}

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.*
import oauth4s.http.HttpResponse
import oauth4s.testkit.TestIssuer
import oauth4s.json.{Json, JsonCodec}
import oauth4s.Fixtures.{ClientId, Redirect}
import oauth4s.jwt.{JwsAlgorithm, Jwt, JwtError, JwtException}

final class OidcClientSpec extends AnyWordSpec, Matchers:
  import OidcClientSpec.*

  "authorize" should {
    "always ask for openid and a nonce, on top of the scope and the PKCE challenge" in {
      val flow = clientOf(RecordingHttp.routes()).authorize(Redirect, AuthorizationOptions.scoped("email")).get
      val sent = AuthorizationCallback.fromUrl(flow.url)

      (sent.parameter("scope"), sent.parameter("nonce"), flow.nonce.isDefined, sent.parameter("code_challenge").isDefined) shouldBe (
        Some("openid email"),
        flow.nonce.map(_.value),
        true,
        true,
      )
    }
  }

  "complete" should {
    "return the tokens with the verified ID token, whose claims read as the user" in {
      val tokens = completed(flow => Some(idToken(flow, "email" -> Json.str("ann@example.com"), "email_verified" -> Json.str("true")))).get

      (tokens.response.accessToken.value, tokens.idToken.claims.subject, tokens.user.email, tokens.user.emailVerified, tokens.idToken.header.keyId) shouldBe (
        "access-1",
        Some("user-1"),
        Some("ann@example.com"),
        Some(true),
        Some("test-key"),
      )
    }

    "refuse an ID token with another nonce, for another client, from another issuer, or signed by another key" in {
      val stranger = TestIssuer(Fixtures.Issuer)

      (
        jwtError(completed(_ => Some(Identity.idToken(ClientId, "user-1", ManualTime.Start, "nonce" -> Json.str("replayed")).value))),
        jwtError(completed(flow => Some(Identity.idToken(oauth4s.ClientId("other"), "user-1", ManualTime.Start, nonce(flow)).value))),
        jwtError(completed(flow => Some(idToken(flow, "iss" -> Json.str("https://evil.example.com"))))),
        jwtError(completed(flow => Some(stranger.idToken(ClientId, "user-1", ManualTime.Start, nonce(flow)).value))),
      ) shouldBe (
        JwtError.NonceMismatch,
        JwtError.AudienceMismatch("client-1", Vector("other")),
        JwtError.IssuerMismatch(Set("https://auth.example.com"), Some("https://evil.example.com")),
        JwtError.InvalidSignature,
      )
    }

    "refuse an ID token bound to another access token, or issued to another party among several audiences" in {
      val audiences = "aud" -> Json.strings(List("client-1", "client-2"))

      (
        jwtError(completed(flow => Some(idToken(flow, "at_hash" -> Json.str(JwsAlgorithm.RS256.leftHash("another-token")))))),
        completed(flow => Some(idToken(flow, "at_hash" -> Json.str(JwsAlgorithm.RS256.leftHash("access-1"))))).isSuccess,
        jwtError(completed(flow => Some(idToken(flow, audiences)))),
        jwtError(completed(flow => Some(idToken(flow, audiences, "azp" -> Json.str("client-2"))))),
        completed(flow => Some(idToken(flow, audiences, "azp" -> Json.str("client-1")))).isSuccess,
      ) shouldBe (
        JwtError.InvalidClaim("at_hash", "it does not match the access token it came with"),
        true,
        JwtError.MissingClaim("azp"),
        JwtError.InvalidClaim("azp", "the token was issued to 'client-2'"),
        true,
      )
    }

    "fail when the token response carries no ID token at all" in {
      completed(_ => None).failed.map { case error: OAuthException => error.error }.get shouldBe OAuthError.MissingField("id_token")
    }
  }

  "userInfo" should {
    "read the claims with the access token as a bearer, and insist on a subject" in {
      val http   =
        RecordingHttp.sequence(HttpResponse.json("""{"sub":"user-1","name":"Ann","picture":"https://cdn.example.com/a.png"}"""), HttpResponse.json("""{"name":"Ann"}"""))
      val client = clientOf(http)
      val info   = client.userInfo(AccessToken("access-1")).get

      (info.subject, info.name, info.picture, http.last.header("Authorization"), client.userInfo(AccessToken("access-1")).failed.map(_.getMessage).get) shouldBe (
        Some("user-1"),
        Some("Ann"),
        Some("https://cdn.example.com/a.png"),
        Some("Bearer access-1"),
        "'sub' is missing from the response",
      )
    }
  }

  "logoutUrl" should {
    "point at the end session endpoint with the hint, the return address and the state" in {
      clientOf(RecordingHttp.routes()).logoutUrl(Some(IdToken("id.token.value")), Some(RedirectUri("https://app.example.com/bye")), Some(State("s1"))) shouldBe Right(
        "https://auth.example.com/logout?client_id=client-1&id_token_hint=id.token.value&post_logout_redirect_uri=https%3A%2F%2Fapp.example.com%2Fbye&state=s1"
      )
    }
  }

  "discover" should {
    "build the client from the OpenID configuration, and refuse one that publishes no keys" in {
      val document = DiscoverySpec.metadata(Fixtures.Issuer)
      val keyless  = document.replace(""""jwks_uri":"https://auth.example.com/jwks",""", "")
      val location = "https://auth.example.com/.well-known/openid-configuration"

      def discovered(body: String) =
        OidcClient.discover[Try](Fixtures.Issuer, ClientId, ClientAuthentication.Public)(using
          ManualTime(),
          RecordingHttp.routes(location -> HttpResponse.json(body)),
          JsonCodec.native
        )

      (discovered(document).map(_.config.client.endpoints.userInfo.map(_.value)).get, discovered(keyless).failed.map(_.getMessage).get) shouldBe (
        Some("https://auth.example.com/userinfo"),
        "'jwks_uri' is missing from the response",
      )
    }
  }

object OidcClientSpec:
  val Identity: TestIssuer = TestIssuer(Fixtures.Issuer)
  val Jwks: Endpoint       = Endpoint("https://auth.example.com/jwks")

  val Config: OidcConfig =
    val endpoints =
      Fixtures.Endpoints.withJwks(Jwks).withUserInfo(Endpoint("https://auth.example.com/userinfo")).withEndSession(Endpoint("https://auth.example.com/logout"))

    OidcConfig(OAuthClientConfig(ClientId, ClientAuthentication.SecretBasic(Fixtures.ClientSecret), endpoints), Fixtures.Issuer)

  def clientOf(http: RecordingHttp, time: ManualTime = ManualTime()): OidcClient[Try] = OidcClient[Try](Config)(using time, http, JsonCodec.native)

  def nonce(flow: AuthorizationFlow): (String, Json) = "nonce" -> Json.str(flow.nonce.fold("")(_.value))

  def idToken(flow: AuthorizationFlow, claims: (String, Json)*): String =
    val overridden = claims.map(_._1).toSet
    val base       = Identity.idToken(ClientId, "user-1", ManualTime.Start, nonce(flow)).value
    val standard   = Jwt.parse(base).toOption.flatMap(_.claims.raw.asObject).getOrElse(Vector.empty)

    Identity.sign(Json.Obj(standard.filterNot((name, _) => overridden(name)) ++ claims))

  def completed(issue: AuthorizationFlow => Option[String]): Try[OidcTokens] =
    val issued = AtomicReference(Option.empty[String])
    val http   = RecordingHttp { request =>
      if request.url == Fixtures.Token.value then
        HttpResponse.json(s"""{"access_token":"access-1","expires_in":3600${issued.get.fold("")(token => s""","id_token":"$token"""")}}""")
      else if request.url == Jwks.value then HttpResponse.json(Identity.jwksJson.render)
      else RecordingHttp.NotFound
    }
    val client = clientOf(http)
    val flow   = client.authorize(Redirect).get

    issued.set(issue(flow))
    client.complete(flow, AuthorizationCallback.fromUrl(s"${Redirect.value}?code=abc&state=${flow.state.value}"))
  end completed

  def jwtError[A](result: Try[A]): JwtError =
    result match
      case Failure(exception: JwtException) => exception.error
      case other                            => throw AssertionError(s"expected a JwtError, got $other")
