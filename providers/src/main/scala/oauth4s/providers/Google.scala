package oauth4s.providers

import oauth4s.*
import oauth4s.http.HttpClient
import oauth4s.json.{Json, JsonCodec}
import oauth4s.oidc.{OidcClient, OidcConfig}
import oauth4s.providers.internal.ProviderImpl

object Google:
  val Name: String        = "google"
  val Issuer: Issuer      = oauth4s.Issuer("https://accounts.google.com")
  val DefaultScope: Scope = Scope.of("openid", "email", "profile")

  val Authorization: Endpoint = Endpoint("https://accounts.google.com/o/oauth2/v2/auth")
  val Token: Endpoint         = Endpoint("https://oauth2.googleapis.com/token")
  val Revocation: Endpoint    = Endpoint("https://oauth2.googleapis.com/revoke")
  val UserInfo: Endpoint      = Endpoint("https://openidconnect.googleapis.com/v1/userinfo")
  val Jwks: Endpoint          = Endpoint("https://www.googleapis.com/oauth2/v3/certs")

  val Offline: AuthorizationOptions = AuthorizationOptions.default.withParameter("access_type", "offline").withPrompt("consent")

  def config(clientId: ClientId, clientSecret: ClientSecret): OidcConfig =
    val endpoints = Endpoints.of(Authorization, Token).withRevocation(Revocation).withUserInfo(UserInfo).withJwks(Jwks)
    val client    = OAuthClientConfig(clientId, ClientAuthentication.SecretPost(clientSecret), endpoints).withScope(DefaultScope)

    OidcConfig(client, Issuer).withValidation(_.withIssuer(Issuer.value, "accounts.google.com"))

  def apply[F[*]](clientId: ClientId, clientSecret: ClientSecret)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    from(config(clientId, clientSecret))

  def from[F[*]](config: OidcConfig)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    val client = OidcClient[F](config)
    val fetch  = ProviderImpl.fetched(client.oauth, UserInfo, Nil)(profile)

    ProviderImpl.oidc(Name, client, pkce = true)((tokens, _) => fetch(tokens))

  def profile(json: Json): Either[OAuthError, UserProfile] =
    ProviderImpl.required(json, "sub").map { id =>
      UserProfile(
        provider = Name,
        id = id,
        name = json.string("name"),
        username = None,
        email = json.string("email"),
        emailVerified = json.boolean("email_verified"),
        picture = json.string("picture"),
        raw = json,
      )
    }
