package oauth4s.providers

import java.util.Base64
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.{KeyPair, KeyPairGenerator}
import java.util.concurrent.atomic.AtomicReference

import scala.util.Try

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.*
import oauth4s.testkit.TestIssuer
import oauth4s.json.{Json, JsonCodec}
import oauth4s.http.{HttpRequest, HttpResponse}
import oauth4s.jwt.{Jwk, JwkKey, Jwks, JwsAlgorithm, JwtException, JwtValidation, JwtVerifier}

final class ProvidersSpec extends AnyWordSpec, Matchers:
  import ProvidersSpec.*

  "Google" should {
    "log a user in over OpenID Connect and read the profile from the userinfo endpoint" in {
      val identity = TestIssuer(Google.Issuer)
      val profile  = """{"sub":"g-1","name":"Ann Lee","email":"ann@example.com","email_verified":true,"picture":"https://lh3.example.com/a"}"""
      val scenario = Scenario(Google[Try](Id, Secret), Google.Token)(flow =>
        Map(
          Google.Token.value    -> token("id_token" -> Json.str(identity.idToken(Id, "g-1", ManualTime.Start, nonce(flow)).value)),
          Google.Jwks.value     -> HttpResponse.json(identity.jwksJson.render),
          Google.UserInfo.value -> HttpResponse.json(profile),
        )
      )

      (
        scenario.sent("scope"),
        scenario.sent.contains("code_challenge"),
        scenario.form.get("client_secret"),
        scenario.login.map(_.profile.copy(raw = Json.Null)).get
      ) shouldBe (
        "openid email profile",
        true,
        Some("secret-1"),
        UserProfile("google", "g-1", Some("Ann Lee"), None, Some("ann@example.com"), Some(true), Some("https://lh3.example.com/a"), Json.Null),
      )
    }

    "ask for a refresh token with the options it publishes for that" in {
      val flow = Google[Try](Id, Secret)(using ManualTime(), RecordingHttp.routes(), JsonCodec.native).authorize(Redirect, Google.Offline).get
      val sent = AuthorizationCallback.fromUrl(flow.url)

      (sent.parameter("access_type"), sent.parameter("prompt")) shouldBe (Some("offline"), Some("consent"))
    }
  }

  "Microsoft" should {
    "accept an ID token from the tenant it names in tid, on the multi-tenant endpoint, and refuse one whose issuer is another tenant's" in {
      val tenant   = "72f988bf-86f1-41af-91ab-2d7cd011db47"
      val identity = TestIssuer(Microsoft.issuer(tenant))
      val keys     = "https://login.microsoftonline.com/common/discovery/v2.0/keys"
      val endpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token"

      def scenario(tid: String) =
        Scenario(Microsoft[Try](Id, Secret), Endpoint(endpoint))(flow =>
          Map(
            endpoint                 -> token("id_token" -> Json.str(identity.idToken(Id, "m-1", ManualTime.Start, nonce(flow), "tid" -> Json.str(tid)).value)),
            keys                     -> HttpResponse.json(identity.jwksJson.render),
            Microsoft.UserInfo.value -> HttpResponse.json("""{"sub":"m-1","name":"Ann Lee","email":"ann@contoso.com"}"""),
          )
        )

      (
        scenario(tenant).login.map(_.profile.email).get,
        scenario("00000000-0000-0000-0000-000000000000").login.failed.map { case error: JwtException => error.error.message.take(15) }.get
      ) shouldBe (
        Some("ann@contoso.com"),
        "the token comes",
      )
    }
  }

  "Apple" should {
    "sign its client secret with the team's key, skip PKCE, ask for form_post, and take the name from the first callback" in {
      val identity = TestIssuer(Apple.Issuer)
      val key      = ecPair()
      val scenario = Scenario(
        Apple[Try](Id, "TEAM123456", "KEY1234567", key.getPrivate),
        Apple.Token,
        user = Some("""{"name":{"firstName":"Ann","lastName":"Lee"},"email":"ann@privaterelay.appleid.com"}""")
      )(flow =>
        Map(
          Apple.Token.value -> token(
            "id_token" -> Json.str(
              identity.idToken(Id, "a-1", ManualTime.Start, nonce(flow), "email" -> Json.str("ann@privaterelay.appleid.com"), "email_verified" -> Json.str("true")).value
            )
          ),
          Apple.Jwks.value  -> HttpResponse.json(identity.jwksJson.render),
        )
      )

      val login  = scenario.login.get
      val secret = JwtVerifier.verify(
        scenario.form("client_secret"),
        Jwks.of(ecJwk(key, "KEY1234567")),
        JwtValidation.of("TEAM123456", Apple.Issuer.value).withAlgorithms(JwsAlgorithm.ES256),
        ManualTime.Start
      )

      (
        scenario.sent.get("response_mode"),
        scenario.sent.contains("code_challenge"),
        scenario.form.contains("code_verifier"),
        secret.map(jwt => (jwt.claims.subject, jwt.header.keyId, jwt.claims.expiresAt.map(_.getEpochSecond - ManualTime.Start.getEpochSecond))),
        login.profile.copy(raw = Json.Null),
      ) shouldBe (
        Some("form_post"),
        false,
        false,
        Right((Some("client-1"), Some("KEY1234567"), Some(300L))),
        UserProfile("apple", "a-1", Some("Ann Lee"), None, Some("ann@privaterelay.appleid.com"), Some(true), None, Json.Null),
      )
    }
  }

  "Facebook" should {
    "separate scopes with commas, skip PKCE, and read the nested picture from the Graph API" in {
      val me       = "https://graph.facebook.com/v27.0/me"
      val endpoint = "https://graph.facebook.com/v27.0/oauth/access_token"
      val scenario = Scenario(Facebook[Try](Id, Secret), Endpoint(endpoint))(_ =>
        Map(
          endpoint -> token(),
          me       -> HttpResponse.json("""{"id":"10158","name":"Ann Lee","email":"ann@example.com","picture":{"data":{"url":"https://scontent.example.com/a.jpg"}}}""")
        )
      )

      (scenario.authorization, scenario.sent("scope"), scenario.sent.contains("code_challenge"), scenario.login.map(_.profile.picture).get, scenario.query(me)) shouldBe (
        "https://www.facebook.com/v27.0/dialog/oauth",
        "public_profile,email",
        false,
        Some("https://scontent.example.com/a.jpg"),
        Map("fields" -> "id,name,email,picture"),
      )
    }
  }

  "Instagram" should {
    "read the token whether or not it comes wrapped in data, and the profile of the professional account" in {
      val me      = "https://graph.instagram.com/v27.0/me"
      val wrapped = HttpResponse.json("""{"data":[{"access_token":"ig-1","user_id":"178414","permissions":"instagram_business_basic"}]}""")
      val profile = HttpResponse.json("""{"user_id":"178414","username":"ann.lee","name":"Ann Lee","profile_picture_url":"https://cdn.example.com/a.jpg"}""")
      val logins  =
        List(wrapped, token()).map(reply => Scenario(Instagram[Try](Id, Secret), Instagram.Token)(_ => Map(Instagram.Token.value -> reply, me -> profile)).login.get)

      (logins.map(_.tokens.accessToken.value), logins.map(_.profile.username).distinct, logins.map(_.profile.id).distinct) shouldBe (
        List("ig-1", "access-1"),
        List(Some("ann.lee")),
        List("178414"),
      )
    }
  }

  "Strava" should {
    "take the athlete that comes with the token instead of asking for it again" in {
      val reply    = token(
        "athlete" -> Json
          .obj("id" -> Json.num(134815), "firstname" -> Json.str("Ann"), "lastname" -> Json.str("Lee"), "profile" -> Json.str("https://cdn.example.com/a.jpg"))
      )
      val scenario = Scenario(Strava[Try](Id, Secret), Strava.Token)(_ => Map(Strava.Token.value -> reply))
      val login    = scenario.login.get

      (scenario.sent("scope"), login.profile.id, login.profile.name, login.profile.email, scenario.requests.map(_.url)) shouldBe (
        "read",
        "134815",
        Some("Ann Lee"),
        None,
        Vector("https://www.strava.com/oauth/token"),
      )
    }
  }

  "VK ID" should {
    "send no secret, hand back the device_id and state of the callback, and post for the profile" in {
      val scenario = Scenario(VkId[Try](Id), VkId.Token, extra = "&device_id=dev-7")(_ =>
        Map(
          VkId.Token.value    -> token("user_id" -> Json.num(1234567)),
          VkId.UserInfo.value -> HttpResponse.json(
            """{"user":{"user_id":"1234567","first_name":"Анна","last_name":"Ли","avatar":"https://cdn.example.com/a.jpg","email":"ann@example.com"}}"""
          ),
        )
      )

      val login = scenario.login.get

      (
        scenario.sent("code_challenge_method"),
        scenario.sent("state").length >= 32,
        scenario.form.get("device_id"),
        scenario.form.get("state"),
        scenario.form.contains("client_secret"),
        login.profile.copy(raw = Json.Null),
        scenario.requests.last.body,
      ) shouldBe (
        "S256",
        true,
        Some("dev-7"),
        Some(scenario.flow.state.value),
        false,
        UserProfile("vk", "1234567", Some("Анна Ли"), None, Some("ann@example.com"), None, Some("https://cdn.example.com/a.jpg"), Json.Null),
        Some("client_id=client-1&access_token=access-1"),
      )
    }
  }

  "X" should {
    "authenticate a confidential client with Basic, keep PKCE on, and read the profile from data" in {
      val scenario = Scenario(X[Try](Id, Secret), X.Token)(_ =>
        Map(
          X.Token.value -> token(),
          X.Me.value    -> HttpResponse.json(
            """{"data":{"id":"2244994945","name":"Ann Lee","username":"annlee","profile_image_url":"https://pbs.example.com/a.jpg","confirmed_email":"ann@example.com"}}"""
          ),
        )
      )

      val login = scenario.login.get

      (
        scenario.authorization,
        scenario.sent("scope"),
        scenario.tokenRequest.header("Authorization"),
        scenario.form.contains("code_verifier"),
        login.profile.copy(raw = Json.Null)
      ) shouldBe (
        "https://x.com/i/oauth2/authorize",
        "tweet.read users.read",
        Some(s"Basic ${Base64.getEncoder.encodeToString("client-1:secret-1".getBytes)}"),
        true,
        UserProfile("x", "2244994945", Some("Ann Lee"), Some("annlee"), Some("ann@example.com"), Some(true), Some("https://pbs.example.com/a.jpg"), Json.Null),
      )
    }
  }

object ProvidersSpec:
  val Id: ClientId          = ClientId("client-1")
  val Secret: ClientSecret  = ClientSecret("secret-1")
  val Redirect: RedirectUri = RedirectUri("https://app.example.com/callback")

  type Wiring = (Effect[Try], RecordingHttp, JsonCodec) ?=> Provider[Try]

  final class Scenario(wiring: Wiring, tokenEndpoint: Endpoint, user: Option[String], extra: String)(replies: AuthorizationFlow => Map[String, HttpResponse]):
    private val routes = AtomicReference(Map.empty[String, HttpResponse])
    private val http   = RecordingHttp(request => routes.get.getOrElse(request.url.takeWhile(_ != '?'), RecordingHttp.NotFound))

    val provider: Provider[Try] = wiring(using ManualTime(), http, JsonCodec.native)
    val flow: AuthorizationFlow = provider.authorize(Redirect).get

    routes.set(replies(flow))

    private val callback = AuthorizationCallback.fromParameters(
      AuthorizationCallback.fromUrl(s"${Redirect.value}?code=abc&state=${flow.state.value}$extra").parameters ++ user.map("user" -> _)
    )

    val login: Try[Login] = provider.login(flow, callback)

    def authorization: String         = flow.url.takeWhile(_ != '?')
    def sent: Map[String, String]     = AuthorizationCallback.fromUrl(flow.url).parameters.toMap
    def requests: Vector[HttpRequest] = http.requests
    def tokenRequest: HttpRequest     = http.requests.find(_.url == tokenEndpoint.value).get
    def form: Map[String, String]     = tokenRequest.body.fold(Map.empty)(body => AuthorizationCallback.fromForm(body).parameters.toMap)

    def query(url: String): Map[String, String] =
      http.requests.find(_.url.startsWith(url)).fold(Map.empty)(request => AuthorizationCallback.fromUrl(request.url).parameters.toMap)

  object Scenario:
    def apply(wiring: Wiring, tokenEndpoint: Endpoint, user: Option[String] = None, extra: String = "")(
        replies: AuthorizationFlow => Map[String, HttpResponse]
    ): Scenario =
      new Scenario(wiring, tokenEndpoint, user, extra)(replies)

  def nonce(flow: AuthorizationFlow): (String, Json) = "nonce" -> Json.str(flow.nonce.fold("")(_.value))

  def token(fields: (String, Json)*): HttpResponse =
    HttpResponse.json(Json.Obj(Vector("access_token" -> Json.str("access-1"), "token_type" -> Json.str("bearer"), "expires_in" -> Json.num(3600)) ++ fields).render)

  def ecPair(): KeyPair =
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(ECGenParameterSpec("secp256r1"))
    generator.generateKeyPair()

  def ecJwk(pair: KeyPair, keyId: String): Jwk =
    val point = pair.getPublic.asInstanceOf[ECPublicKey].getW

    Jwk(JwkKey.Ec("P-256", BigInt(point.getAffineX), BigInt(point.getAffineY)), Some(keyId), None, Some("sig"))
