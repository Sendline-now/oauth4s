package oauth4s.providers

import java.time.Instant
import java.security.PrivateKey

import scala.concurrent.duration.{FiniteDuration, MINUTES}

import oauth4s.*
import oauth4s.http.HttpClient
import oauth4s.json.{Json, JsonCodec}
import oauth4s.oidc.{OidcClient, OidcConfig}
import oauth4s.providers.internal.ProviderImpl
import oauth4s.jwt.{JwsAlgorithm, JwtClaims, JwtSigner}

object Apple:
  val Name: String        = "apple"
  val Issuer: Issuer      = oauth4s.Issuer("https://appleid.apple.com")
  val DefaultScope: Scope = Scope.of("openid", "name", "email")

  val Authorization: Endpoint = Endpoint("https://appleid.apple.com/auth/authorize")
  val Token: Endpoint         = Endpoint("https://appleid.apple.com/auth/token")
  val Revocation: Endpoint    = Endpoint("https://appleid.apple.com/auth/revoke")
  val Jwks: Endpoint          = Endpoint("https://appleid.apple.com/auth/keys")

  val SecretLifetime: FiniteDuration = FiniteDuration(5, MINUTES)

  def clientSecret(clientId: ClientId, teamId: String, keyId: String, key: PrivateKey, issuedAt: Instant)(using JsonCodec): ClientSecret =
    val claims = Json.obj(
      "iss" -> Json.str(teamId),
      "iat" -> Json.num(issuedAt.getEpochSecond),
      "exp" -> Json.num(issuedAt.getEpochSecond + SecretLifetime.toSeconds),
      "aud" -> Json.str(Issuer.value),
      "sub" -> Json.str(clientId.value),
    )

    JwtSigner.sign(claims, JwsAlgorithm.ES256, key, Some(keyId)).fold(error => throw error.toThrowable, ClientSecret(_))

  def config(clientId: ClientId, teamId: String, keyId: String, key: PrivateKey)(using JsonCodec): OidcConfig =
    require(
      key.getAlgorithm == "EC",
      "Apple signs client secrets with ES256 — the key from the .p8 file is an EC key; read it with JwtSigner.privateKey(pem, \"EC\")"
    )

    val endpoints = Endpoints.of(Authorization, Token).withRevocation(Revocation).withJwks(Jwks)
    val minted    = ClientAuthentication.SecretMinted(now => clientSecret(clientId, teamId, keyId, key, now))
    val client    = OAuthClientConfig(clientId, minted, endpoints).withScope(DefaultScope).withAuthorizationParameter("response_mode", "form_post")

    OidcConfig(client, Issuer)
  end config

  def apply[F[*]](clientId: ClientId, teamId: String, keyId: String, key: PrivateKey)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    from(config(clientId, teamId, keyId, key))

  def from[F[*]](config: OidcConfig)(using F: Effect[F], http: HttpClient[F], codec: JsonCodec): Provider[F] =
    val client = OidcClient[F](config)

    ProviderImpl.oidc(Name, client, pkce = false) { (tokens, callback) =>
      tokens.idToken match
        case None        => F.raiseError(OAuthError.MissingField("id_token").toThrowable)
        case Some(token) =>
          F.flatMap(client.verify(token)) { jwt =>
            val user = callback.parameter("user").flatMap(text => codec.parse(text).toOption)

            Effect.fromEither(profile(jwt.claims, user).left.map(_.toThrowable))
          }
    }
  end from

  def profile(claims: JwtClaims, user: Option[Json]): Either[OAuthError, UserProfile] =
    claims.subject.toRight(OAuthError.MissingField("sub")).map { id =>
      UserProfile(
        provider = Name,
        id = id,
        name = user.flatMap(json => ProviderImpl.joined(json.path("name", "firstName").flatMap(_.asString), json.path("name", "lastName").flatMap(_.asString))),
        username = None,
        email = claims.string("email").orElse(user.flatMap(_.string("email"))),
        emailVerified = claims.flag("email_verified"),
        picture = None,
        raw = claims.raw,
      )
    }
