package oauth4s.providers

import oauth4s.json.Json
import oauth4s.oidc.{OidcClient, UserInfo}
import oauth4s.providers.internal.ProviderImpl
import oauth4s.{AuthorizationCallback, AuthorizationFlow, AuthorizationOptions, Effect, Endpoint, OAuthClient, OAuthError, RedirectUri, TokenResponse}

final case class UserProfile(
    provider: String,
    id: String,
    name: Option[String],
    username: Option[String],
    email: Option[String],
    emailVerified: Option[Boolean],
    picture: Option[String],
    raw: Json,
)

final case class Login(tokens: TokenResponse, profile: UserProfile)

trait Provider[F[*]]:
  def name: String
  def oauth: OAuthClient[F]

  def authorize(redirectUri: RedirectUri, options: AuthorizationOptions = AuthorizationOptions.default): F[AuthorizationFlow]

  def login(flow: AuthorizationFlow, callback: AuthorizationCallback): F[Login]

  def profile(tokens: TokenResponse): F[UserProfile]

object Provider:

  def oauth[F[*]](name: String, client: OAuthClient[F], profileEndpoint: Endpoint, parameters: Seq[(String, String)] = Nil)(
      profile: Json => Either[OAuthError, UserProfile]
  )(using Effect[F]): Provider[F] =
    ProviderImpl.oauth(name, client, pkce = true)(ProviderImpl.fetched(client, profileEndpoint, parameters)(profile))

  def oidc[F[*]](name: String, client: OidcClient[F])(using F: Effect[F]): Provider[F] =
    ProviderImpl.oidc(name, client, pkce = true)((tokens, _) => F.map(client.userInfo(tokens.accessToken))(profile(name, _)))

  def profile(name: String, info: UserInfo): UserProfile =
    UserProfile(
      provider = name,
      id = info.subject.getOrElse(""),
      name = info.name,
      username = info.preferredUsername,
      email = info.email,
      emailVerified = info.emailVerified,
      picture = info.picture,
      raw = info.raw,
    )
