package oauth4s.testkit

import java.util.Base64
import java.time.Instant
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.atomic.AtomicInteger

import com.sun.net.httpserver.{HttpExchange, HttpServer}

import oauth4s.*
import oauth4s.internal.Urls
import oauth4s.jwt.JwsAlgorithm
import oauth4s.json.{Json, JsonCodec}

final class FakeAuthorizationServer private (server: HttpServer, val clientId: ClientId, val clientSecret: ClientSecret, val subject: String):
  import FakeAuthorizationServer.{Device, Grant, Reply}

  private val codec    = JsonCodec.native
  private val counter  = AtomicInteger(0)
  private val codes    = ConcurrentHashMap[String, Grant]()
  private val refresh  = ConcurrentHashMap[String, Grant]()
  private val access   = ConcurrentHashMap[String, Grant]()
  private val devices  = ConcurrentHashMap[String, Device]()
  private val identity = TestIssuer(issuer)

  def port: Int = server.getAddress.getPort

  def issuer: Issuer = Issuer(s"http://127.0.0.1:$port")

  def endpoint(path: String): Endpoint = Endpoint(s"${issuer.value}$path")

  def endpoints: Endpoints =
    Endpoints
      .of(endpoint("/authorize"), endpoint("/token"))
      .withDeviceAuthorization(endpoint("/device"))
      .withRevocation(endpoint("/revoke"))
      .withIntrospection(endpoint("/introspect"))
      .withUserInfo(endpoint("/userinfo"))
      .withJwks(endpoint("/jwks"))
      .withEndSession(endpoint("/logout"))

  def approve(authorizationUrl: String): String =
    val query = Urls.queryOf(authorizationUrl).toMap
    val code  = s"code-${counter.incrementAndGet()}"

    codes.put(code, Grant(Scope.parse(query.getOrElse("scope", "")), query.get("redirect_uri"), query.get("code_challenge"), query.get("nonce")))
    Urls.withQuery(query.getOrElse("redirect_uri", ""), Vector("code" -> code, "iss" -> issuer.value) ++ query.get("state").map("state" -> _))

  def approveDevice(userCode: UserCode): Unit =
    devices.forEach((_, device) => if device.userCode == userCode.value then device.approved.set(1))

  def close(): Unit = server.stop(0)

  private def start(): FakeAuthorizationServer =
    route("/.well-known/openid-configuration")(_ => Reply(200, metadata))
    route("/.well-known/oauth-authorization-server")(_ => Reply(200, metadata))
    route("/jwks")(_ => Reply(200, identity.jwksJson))
    route("/token")(exchange => authenticated(exchange)(token))
    route("/device")(exchange => authenticated(exchange)(device))
    route("/revoke")(exchange => authenticated(exchange)(revoke))
    route("/introspect")(exchange => authenticated(exchange)(introspect))
    route("/userinfo")(userInfo)
    server.start()
    this

  private def metadata: Json =
    Json.obj(
      "issuer"                           -> Json.str(issuer.value),
      "authorization_endpoint"           -> Json.str(endpoint("/authorize").value),
      "token_endpoint"                   -> Json.str(endpoint("/token").value),
      "device_authorization_endpoint"    -> Json.str(endpoint("/device").value),
      "revocation_endpoint"              -> Json.str(endpoint("/revoke").value),
      "introspection_endpoint"           -> Json.str(endpoint("/introspect").value),
      "userinfo_endpoint"                -> Json.str(endpoint("/userinfo").value),
      "jwks_uri"                         -> Json.str(endpoint("/jwks").value),
      "end_session_endpoint"             -> Json.str(endpoint("/logout").value),
      "code_challenge_methods_supported" -> Json.strings(List("S256")),
      "grant_types_supported"            -> Json.strings(List("authorization_code", "client_credentials", "refresh_token", GrantType.DeviceCode.value)),
    )

  private def token(form: Map[String, String]): Reply =
    form.get("grant_type") match
      case Some("authorization_code")                         =>
        Option(codes.remove(form.getOrElse("code", ""))) match
          case None                                                         => rejected("invalid_grant", "the code is unknown or was already used")
          case Some(grant) if grant.redirectUri != form.get("redirect_uri") => rejected("invalid_grant", "the redirect_uri differs from the authorization request")
          case Some(grant) if !grant.verifiedBy(form.get("code_verifier"))  => rejected("invalid_grant", "the code_verifier does not match the code_challenge")
          case Some(grant)                                                  => issued(grant, refreshable = true)
      case Some("refresh_token")                              =>
        Option(refresh.remove(form.getOrElse("refresh_token", "")))
          .fold(rejected("invalid_grant", "the refresh token is unknown or was rotated"))(issued(_, refreshable = true))
      case Some("client_credentials")                         => issued(Grant(Scope.parse(form.getOrElse("scope", "")), None, None, None), refreshable = false)
      case Some(grant) if grant == GrantType.DeviceCode.value =>
        Option(devices.get(form.getOrElse("device_code", ""))) match
          case None                                     => rejected("invalid_grant", "the device code is unknown")
          case Some(device) if device.approved.get == 0 => rejected("authorization_pending", "the user has not approved the device yet")
          case Some(device)                             => issued(Grant(device.scope, None, None, None), refreshable = true)
      case other                                              => rejected("unsupported_grant_type", s"${other.getOrElse("no grant type")} is not supported")

  private def issued(grant: Grant, refreshable: Boolean): Reply =
    val now          = Instant.now()
    val accessToken  = s"access-${counter.incrementAndGet()}"
    val refreshToken = Option.when(refreshable)(s"refresh-${counter.incrementAndGet()}")
    val idToken      = Option.when(grant.scope.contains("openid")) {
      val claims = Vector("at_hash" -> Json.str(JwsAlgorithm.RS256.leftHash(accessToken))) ++ grant.nonce.map("nonce" -> Json.str(_))

      identity.idToken(clientId, subject, now, claims*)(using codec)
    }

    access.put(accessToken, grant)
    refreshToken.foreach(refresh.put(_, grant))

    Reply(
      200,
      Json.Obj(
        Vector("access_token" -> Json.str(accessToken), "token_type" -> Json.str("Bearer"), "expires_in" -> Json.num(3600), "scope" -> Json.str(grant.scope.render)) ++
          refreshToken.map("refresh_token" -> Json.str(_)) ++
          idToken.map(token => "id_token" -> Json.str(token.value))
      ),
    )
  end issued

  private def device(form: Map[String, String]): Reply =
    val number = counter.incrementAndGet()

    devices.put(s"device-$number", Device(s"USER-$number", Scope.parse(form.getOrElse("scope", "")), AtomicInteger(0)))
    Reply(
      200,
      Json.obj(
        "device_code"      -> Json.str(s"device-$number"),
        "user_code"        -> Json.str(s"USER-$number"),
        "verification_uri" -> Json.str(s"${issuer.value}/activate"),
        "expires_in"       -> Json.num(60),
        "interval"         -> Json.num(BigDecimal("0.05")),
      ),
    )

  private def revoke(form: Map[String, String]): Reply =
    val token = form.getOrElse("token", "")

    access.remove(token)
    refresh.remove(token)
    Reply(200, Json.empty)

  private def introspect(form: Map[String, String]): Reply =
    Option(access.get(form.getOrElse("token", ""))) match
      case Some(grant) =>
        Reply(200, Json.obj("active" -> Json.bool(true), "scope" -> Json.str(grant.scope.render), "client_id" -> Json.str(clientId.value), "sub" -> Json.str(subject)))
      case None        => Reply(200, Json.obj("active" -> Json.bool(false)))

  private def userInfo(exchange: HttpExchange): Reply =
    val token = Option(exchange.getRequestHeaders.getFirst("Authorization")).map(_.stripPrefix("Bearer ")).getOrElse("")

    if access.containsKey(token)
    then Reply(200, Json.obj("sub" -> Json.str(subject), "name" -> Json.str("Test User"), "email" -> Json.str("user@example.com"), "email_verified" -> Json.bool(true)))
    else Reply(401, Json.obj("error" -> Json.str("invalid_token")))

  private def authenticated(exchange: HttpExchange)(handle: Map[String, String] => Reply): Reply =
    val form  = Urls.form(String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)).toMap
    val basic = Option(exchange.getRequestHeaders.getFirst("Authorization")).filter(_.startsWith("Basic ")).map { header =>
      String(Base64.getDecoder.decode(header.stripPrefix("Basic ")), StandardCharsets.UTF_8).split(":", 2).toList
    }

    val presented = basic match
      case Some(List(id, secret)) => Some((id, secret))
      case _                      => form.get("client_id").zip(form.get("client_secret"))

    if presented.contains((clientId.value, clientSecret.value))
    then handle(form)
    else Reply(401, Json.obj("error" -> Json.str("invalid_client"), "error_description" -> Json.str("the client credentials are wrong")))
  end authenticated

  private def rejected(code: String, description: String): Reply =
    Reply(400, Json.obj("error" -> Json.str(code), "error_description" -> Json.str(description)))

  private def route(path: String)(handle: HttpExchange => Reply): Unit =
    server.createContext(
      path,
      exchange =>
        val reply = handle(exchange)
        val body  = codec.render(reply.body).getBytes(StandardCharsets.UTF_8)

        exchange.getResponseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(reply.status, body.length.toLong)
        exchange.getResponseBody.write(body)
        exchange.close(),
    ): Unit

object FakeAuthorizationServer:
  private final case class Reply(status: Int, body: Json)

  private final case class Device(userCode: String, scope: Scope, approved: AtomicInteger)

  private final case class Grant(scope: Scope, redirectUri: Option[String], challenge: Option[String], nonce: Option[String]):
    def verifiedBy(verifier: Option[String]): Boolean =
      challenge.forall(expected => verifier.flatMap(CodeVerifier.parse(_).toOption).exists(_.challenge(PkceMethod.S256).value == expected))

  def start(clientId: ClientId = ClientId("test-client"), clientSecret: ClientSecret = ClientSecret("test-secret"), subject: String = "user-1"): FakeAuthorizationServer =
    new FakeAuthorizationServer(HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress, 0), 0), clientId, clientSecret, subject).start()
