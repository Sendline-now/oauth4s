package oauth4s.providers.internal

import oauth4s.*
import oauth4s.json.Json
import oauth4s.oidc.OidcClient
import oauth4s.internal.Requests
import oauth4s.providers.{Login, Provider, UserProfile}

private[providers] final class ProviderImpl[F[*]](
    val name: String,
    val oauth: OAuthClient[F],
    starting: (RedirectUri, AuthorizationOptions) => F[AuthorizationFlow],
    completing: (AuthorizationFlow, AuthorizationCallback) => F[TokenResponse],
    loading: (TokenResponse, AuthorizationCallback) => F[UserProfile],
)(using F: Effect[F])
    extends Provider[F]:

  def authorize(redirectUri: RedirectUri, options: AuthorizationOptions): F[AuthorizationFlow] = starting(redirectUri, options)

  def login(flow: AuthorizationFlow, callback: AuthorizationCallback): F[Login] =
    F.flatMap(completing(flow, callback))(tokens => F.map(loading(tokens, callback))(Login(tokens, _)))

  def profile(tokens: TokenResponse): F[UserProfile] = loading(tokens, AuthorizationCallback.fromParameters(Nil))

private[providers] object ProviderImpl:

  def oauth[F[*]](name: String, client: OAuthClient[F], pkce: Boolean)(loading: TokenResponse => F[UserProfile])(using Effect[F]): Provider[F] =
    ProviderImpl(
      name = name,
      oauth = client,
      starting = (redirectUri, options) => client.authorize(redirectUri, if pkce then options else options.withoutPkce),
      completing = client.complete,
      loading = (tokens, _) => loading(tokens),
    )

  def oidc[F[*]](name: String, client: OidcClient[F], pkce: Boolean)(loading: (TokenResponse, AuthorizationCallback) => F[UserProfile])(using F: Effect[F]): Provider[F] =
    ProviderImpl(
      name = name,
      oauth = client.oauth,
      starting = (redirectUri, options) => client.authorize(redirectUri, if pkce then options else options.withoutPkce),
      completing = (flow, callback) => F.map(client.complete(flow, callback))(_.response),
      loading = loading,
    )

  def fetched[F[*]](client: OAuthClient[F], endpoint: Endpoint, parameters: Seq[(String, String)])(read: Json => Either[OAuthError, UserProfile])(using
      F: Effect[F]
  ): TokenResponse => F[UserProfile] =
    tokens => F.flatMap(client.resource(endpoint, tokens.accessToken, parameters))(json => Requests.lift(read(json)))

  def required(json: Json, name: String): Either[OAuthError, String] =
    json.field(name) match
      case Some(Json.Str(value)) if value.nonEmpty => Right(value)
      case Some(Json.Num(value))                   => Right(value.bigDecimal.toPlainString)
      case _                                       => Left(OAuthError.MissingField(name))

  def joined(parts: Option[String]*): Option[String] = Some(parts.flatten.mkString(" ")).filter(_.nonEmpty)
