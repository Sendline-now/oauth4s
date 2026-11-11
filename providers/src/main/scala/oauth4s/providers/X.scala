package oauth4s.providers

import oauth4s.*
import oauth4s.http.HttpClient
import oauth4s.json.{Json, JsonCodec}
import oauth4s.providers.internal.ProviderImpl

object X:
  val Name: String        = "x"
  val Issuer: Issuer      = oauth4s.Issuer("https://api.x.com")
  val DefaultScope: Scope = Scope.of("tweet.read", "users.read")
  val Offline: Scope      = DefaultScope ++ Scope.of("offline.access")
  val Fields: String      = "profile_image_url,confirmed_email"

  val Authorization: Endpoint = Endpoint("https://x.com/i/oauth2/authorize")
  val Token: Endpoint         = Endpoint("https://api.x.com/2/oauth2/token")
  val Revocation: Endpoint    = Endpoint("https://api.x.com/2/oauth2/revoke")
  val Me: Endpoint            = Endpoint("https://api.x.com/2/users/me")

  def config(clientId: ClientId, authentication: ClientAuthentication): OAuthClientConfig =
    OAuthClientConfig(clientId, authentication, Endpoints.of(Authorization, Token).withRevocation(Revocation).withUserInfo(Me)).withScope(DefaultScope)

  def apply[F[*]](clientId: ClientId, clientSecret: ClientSecret)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    from(config(clientId, ClientAuthentication.SecretBasic(clientSecret)))

  def public[F[*]](clientId: ClientId)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    from(config(clientId, ClientAuthentication.Public))

  def from[F[*]](config: OAuthClientConfig)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    val client = OAuthClient[F](config)

    ProviderImpl.oauth(Name, client, pkce = true)(ProviderImpl.fetched(client, Me, Seq("user.fields" -> Fields))(profile))

  def profile(json: Json): Either[OAuthError, UserProfile] =
    val user = json.field("data").getOrElse(json)

    ProviderImpl.required(user, "id").map { id =>
      UserProfile(
        provider = Name,
        id = id,
        name = user.string("name"),
        username = user.string("username"),
        email = user.string("confirmed_email"),
        emailVerified = user.string("confirmed_email").map(_ => true),
        picture = user.string("profile_image_url"),
        raw = json,
      )
    }
