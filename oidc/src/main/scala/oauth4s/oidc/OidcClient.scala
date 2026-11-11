package oauth4s.oidc

import oauth4s.*
import oauth4s.json.JsonCodec
import oauth4s.http.HttpClient
import oauth4s.jwt.{JwksSource, Jwt}
import oauth4s.oidc.internal.OidcClientImpl

final case class OidcTokens(response: TokenResponse, idToken: Jwt):
  def user: UserInfo = UserInfo(idToken.claims.raw)

trait OidcClient[F[*]]:
  def config: OidcConfig
  def oauth: OAuthClient[F]
  def keys: JwksSource[F]

  def authorize(redirectUri: RedirectUri, options: AuthorizationOptions = AuthorizationOptions.default): F[AuthorizationFlow]

  def complete(flow: AuthorizationFlow, callback: AuthorizationCallback): F[OidcTokens]

  def verify(idToken: IdToken, nonce: Option[Nonce] = None): F[Jwt]

  def userInfo(token: AccessToken): F[UserInfo]

  def logoutUrl(idToken: Option[IdToken] = None, redirectUri: Option[RedirectUri] = None, state: Option[State] = None): Either[OAuthError, String]

object OidcClient:
  val OpenId: Scope = Scope.of("openid")

  def apply[F[*]](config: OidcConfig)(using Effect[F], HttpClient[F], JsonCodec): OidcClient[F] = OidcClientImpl(config)

  def discover[F[*]](issuer: Issuer, clientId: ClientId, authentication: ClientAuthentication)(using
      F: Effect[F],
      http: HttpClient[F],
      codec: JsonCodec,
  ): F[OidcClient[F]] =
    F.flatMap(Discovery.openid[F](issuer)) { metadata =>
      val config = OAuthClientConfig.fromMetadata(metadata, clientId, authentication).flatMap(OidcConfig.fromMetadata(metadata, _))

      F.map(Effect.fromEither(config.left.map(_.toThrowable)))(apply[F])
    }
