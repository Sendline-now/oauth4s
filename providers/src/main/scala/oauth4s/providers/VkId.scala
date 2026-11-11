package oauth4s.providers

import oauth4s.*
import oauth4s.internal.Requests
import oauth4s.json.{Json, JsonCodec}
import oauth4s.http.{HttpClient, HttpHeaders}
import oauth4s.providers.internal.ProviderImpl

object VkId:
  val Name: String        = "vk"
  val DeviceId: String    = "device_id"
  val DefaultScope: Scope = Scope.of("vkid.personal_info", "email")

  val Authorization: Endpoint = Endpoint("https://id.vk.ru/authorize")
  val Token: Endpoint         = Endpoint("https://id.vk.ru/oauth2/auth")
  val UserInfo: Endpoint      = Endpoint("https://id.vk.ru/oauth2/user_info")

  def config(clientId: ClientId): OAuthClientConfig =
    OAuthClientConfig(clientId, ClientAuthentication.Public, Endpoints.of(Authorization, Token).withUserInfo(UserInfo))
      .withScope(DefaultScope)
      .forwardingCallbackParameter(DeviceId)
      .forwardingCallbackParameter("state")

  def config(clientId: ClientId, serviceToken: String): OAuthClientConfig = config(clientId).withTokenParameter("service_token", serviceToken)

  def apply[F[*]](clientId: ClientId)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] = from(config(clientId))

  def from[F[*]](config: OAuthClientConfig)(using F: Effect[F], http: HttpClient[F], codec: JsonCodec): Provider[F] =
    val client = OAuthClient[F](config)

    ProviderImpl.oauth(Name, client, pkce = true) { tokens =>
      val form = Seq("client_id" -> config.clientId.value, "access_token" -> tokens.accessToken.value)

      F.flatMap(Requests.postJson(UserInfo.value, form, HttpHeaders.empty))(json => Requests.lift(profile(json)))
    }

  def refresh[F[*]](provider: Provider[F], token: RefreshToken, deviceId: String, state: State): F[TokenResponse] =
    provider.oauth.refresh(token, parameters = Seq(DeviceId -> deviceId, "state" -> state.value))

  def profile(json: Json): Either[OAuthError, UserProfile] =
    val user = json.field("user").getOrElse(json)

    ProviderImpl.required(user, "user_id").map { id =>
      UserProfile(
        provider = Name,
        id = id,
        name = ProviderImpl.joined(user.string("first_name"), user.string("last_name")),
        username = None,
        email = user.string("email").filter(_.nonEmpty),
        emailVerified = None,
        picture = user.string("avatar"),
        raw = json,
      )
    }
