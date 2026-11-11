package oauth4s.providers

import oauth4s.*
import oauth4s.internal.Requests
import oauth4s.json.{Json, JsonCodec}
import oauth4s.http.{HttpClient, HttpHeaders}
import oauth4s.providers.internal.ProviderImpl

object Instagram:
  val Name: String        = "instagram"
  val Version: String     = "v27.0"
  val DefaultScope: Scope = Scope.of("instagram_business_basic")
  val Fields: String      = "user_id,username,name,account_type,profile_picture_url"

  val Authorization: Endpoint = Endpoint("https://www.instagram.com/oauth/authorize")
  val Token: Endpoint         = Endpoint("https://api.instagram.com/oauth/access_token")
  val LongLived: Endpoint     = Endpoint("https://graph.instagram.com/access_token")
  val Refresh: Endpoint       = Endpoint("https://graph.instagram.com/refresh_access_token")

  def profileEndpoint(version: String = Version): Endpoint = Endpoint(s"https://graph.instagram.com/$version/me")

  def config(clientId: ClientId, clientSecret: ClientSecret, version: String = Version): OAuthClientConfig =
    OAuthClientConfig(clientId, ClientAuthentication.SecretPost(clientSecret), Endpoints.of(Authorization, Token).withUserInfo(profileEndpoint(version)))
      .withScope(DefaultScope)
      .withScopeSeparator(",")
      .adaptingTokenResponse(unwrapped)

  def apply[F[*]](clientId: ClientId, clientSecret: ClientSecret, version: String = Version)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    from(config(clientId, clientSecret, version))

  def from[F[*]](config: OAuthClientConfig)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    val client   = OAuthClient[F](config)
    val endpoint = config.endpoints.userInfo.getOrElse(profileEndpoint())

    ProviderImpl.oauth(Name, client, pkce = false)(ProviderImpl.fetched(client, endpoint, Seq("fields" -> Fields))(profile))

  def longLived[F[*]](clientSecret: ClientSecret, token: AccessToken)(using Effect[F], HttpClient[F], JsonCodec): F[TokenResponse] =
    exchanged(LongLived, Seq("grant_type" -> "ig_exchange_token", "client_secret" -> clientSecret.value, "access_token" -> token.value))

  def refreshed[F[*]](token: AccessToken)(using Effect[F], HttpClient[F], JsonCodec): F[TokenResponse] =
    exchanged(Refresh, Seq("grant_type" -> "ig_refresh_token", "access_token" -> token.value))

  def profile(json: Json): Either[OAuthError, UserProfile] =
    ProviderImpl.required(json, "user_id").orElse(ProviderImpl.required(json, "id")).map { id =>
      UserProfile(
        provider = Name,
        id = id,
        name = json.string("name"),
        username = json.string("username"),
        email = None,
        emailVerified = None,
        picture = json.string("profile_picture_url"),
        raw = json,
      )
    }

  private def unwrapped(json: Json): Json =
    json.array("data").flatMap(_.headOption).getOrElse(json)

  private def exchanged[F[*]](endpoint: Endpoint, parameters: Seq[(String, String)])(using F: Effect[F], http: HttpClient[F], codec: JsonCodec): F[TokenResponse] =
    F.flatMap(Requests.getJson(endpoint.value, parameters, HttpHeaders.empty)) { json =>
      F.flatMap(F.realTime)(now => Requests.lift(TokenResponse.fromJson(json, now)))
    }
