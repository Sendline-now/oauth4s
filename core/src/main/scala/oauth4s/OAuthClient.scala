package oauth4s

import oauth4s.http.HttpClient
import oauth4s.json.{Json, JsonCodec}
import oauth4s.internal.OAuthClientImpl

trait OAuthClient[F[*]]:
  def config: OAuthClientConfig

  def authorize(redirectUri: RedirectUri, options: AuthorizationOptions = AuthorizationOptions.default): F[AuthorizationFlow]

  def complete(flow: AuthorizationFlow, callback: AuthorizationCallback): F[TokenResponse]

  def exchangeCode(
      code: AuthorizationCode,
      redirectUri: RedirectUri,
      verifier: Option[CodeVerifier] = None,
      parameters: Seq[(String, String)] = Nil,
  ): F[TokenResponse]

  def clientCredentials(scope: Scope = Scope.empty): F[TokenResponse]

  def refresh(token: RefreshToken, scope: Scope = Scope.empty, parameters: Seq[(String, String)] = Nil): F[TokenResponse]

  def requestToken(grant: GrantType, parameters: Seq[(String, String)]): F[TokenResponse]

  def deviceAuthorization(scope: Scope = Scope.empty): F[DeviceAuthorization]

  def awaitDevice(authorization: DeviceAuthorization): F[TokenResponse]

  def revokeAccessToken(token: AccessToken): F[Unit]

  def revokeRefreshToken(token: RefreshToken): F[Unit]

  def introspect(token: AccessToken): F[Introspection]

  def resource(endpoint: Endpoint, token: AccessToken, parameters: Seq[(String, String)] = Nil): F[Json]

object OAuthClient:

  def apply[F[*]](config: OAuthClientConfig)(using Effect[F], HttpClient[F], JsonCodec): OAuthClient[F] = OAuthClientImpl(config)

  def discover[F[*]](issuer: Issuer, clientId: ClientId, authentication: ClientAuthentication)(using
      F: Effect[F],
      http: HttpClient[F],
      codec: JsonCodec,
  ): F[OAuthClient[F]] =
    F.flatMap(Discovery.discover[F](issuer)) { metadata =>
      F.map(Effect.fromEither(OAuthClientConfig.fromMetadata(metadata, clientId, authentication).left.map(_.toThrowable)))(apply[F])
    }
