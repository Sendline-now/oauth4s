package oauth4s.providers

import oauth4s.*
import oauth4s.http.HttpClient
import oauth4s.internal.Requests
import oauth4s.json.{Json, JsonCodec}
import oauth4s.providers.internal.ProviderImpl

object Strava:
  val Name: String        = "strava"
  val DefaultScope: Scope = Scope.of("read")

  val Authorization: Endpoint = Endpoint("https://www.strava.com/oauth/authorize")
  val Token: Endpoint         = Endpoint("https://www.strava.com/oauth/token")
  val Athlete: Endpoint       = Endpoint("https://www.strava.com/api/v3/athlete")

  def config(clientId: ClientId, clientSecret: ClientSecret): OAuthClientConfig =
    OAuthClientConfig(clientId, ClientAuthentication.SecretPost(clientSecret), Endpoints.of(Authorization, Token).withUserInfo(Athlete))
      .withScope(DefaultScope)
      .withScopeSeparator(",")

  def apply[F[*]](clientId: ClientId, clientSecret: ClientSecret)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    from(config(clientId, clientSecret))

  def from[F[*]](config: OAuthClientConfig)(using F: Effect[F], http: HttpClient[F], codec: JsonCodec): Provider[F] =
    val client = OAuthClient[F](config)
    val fetch  = ProviderImpl.fetched(client, Athlete, Nil)(profile)

    ProviderImpl.oauth(Name, client, pkce = false) { tokens =>
      tokens.raw.field("athlete") match
        case Some(athlete) => Requests.lift(profile(athlete))
        case None          => fetch(tokens)
    }

  def profile(json: Json): Either[OAuthError, UserProfile] =
    ProviderImpl.required(json, "id").map { id =>
      UserProfile(
        provider = Name,
        id = id,
        name = ProviderImpl.joined(json.string("firstname"), json.string("lastname")),
        username = json.string("username"),
        email = None,
        emailVerified = None,
        picture = json.string("profile"),
        raw = json,
      )
    }
