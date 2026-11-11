package oauth4s.providers

import oauth4s.*
import oauth4s.http.HttpClient
import oauth4s.json.{Json, JsonCodec}
import oauth4s.providers.internal.ProviderImpl

object Facebook:
  val Name: String        = "facebook"
  val Version: String     = "v27.0"
  val DefaultScope: Scope = Scope.of("public_profile", "email")
  val Fields: String      = "id,name,email,picture"

  val LongLived: GrantType = GrantType.Other("fb_exchange_token")

  def profileEndpoint(version: String = Version): Endpoint = Endpoint(s"https://graph.facebook.com/$version/me")

  def config(clientId: ClientId, clientSecret: ClientSecret, version: String = Version): OAuthClientConfig =
    val endpoints = Endpoints
      .of(Endpoint(s"https://www.facebook.com/$version/dialog/oauth"), Endpoint(s"https://graph.facebook.com/$version/oauth/access_token"))
      .withUserInfo(profileEndpoint(version))

    OAuthClientConfig(clientId, ClientAuthentication.SecretPost(clientSecret), endpoints).withScope(DefaultScope).withScopeSeparator(",")

  def apply[F[*]](clientId: ClientId, clientSecret: ClientSecret, version: String = Version)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    from(config(clientId, clientSecret, version))

  def from[F[*]](config: OAuthClientConfig)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    val client   = OAuthClient[F](config)
    val endpoint = config.endpoints.userInfo.getOrElse(profileEndpoint())

    ProviderImpl.oauth(Name, client, pkce = false)(ProviderImpl.fetched(client, endpoint, Seq("fields" -> Fields))(profile))

  def longLived[F[*]](provider: Provider[F], token: AccessToken): F[TokenResponse] =
    provider.oauth.requestToken(LongLived, Seq("fb_exchange_token" -> token.value))

  def profile(json: Json): Either[OAuthError, UserProfile] =
    ProviderImpl.required(json, "id").map { id =>
      UserProfile(
        provider = Name,
        id = id,
        name = json.string("name"),
        username = None,
        email = json.string("email"),
        emailVerified = None,
        picture = json.path("picture", "data", "url").flatMap(_.asString),
        raw = json,
      )
    }
