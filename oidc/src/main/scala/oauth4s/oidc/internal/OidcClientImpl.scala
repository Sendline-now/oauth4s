package oauth4s.oidc.internal

import oauth4s.*
import oauth4s.json.JsonCodec
import oauth4s.http.HttpClient
import oauth4s.internal.{Requests, Urls}
import oauth4s.oidc.{OidcClient, OidcConfig, OidcTokens, UserInfo}
import oauth4s.jwt.{Jwks, JwksSource, JwsAlgorithm, Jwt, JwtClaims, JwtError, JwtVerifier}

private[oidc] final class OidcClientImpl[F[*]](
    val config: OidcConfig,
)(using F: Effect[F], http: HttpClient[F], codec: JsonCodec)
    extends OidcClient[F]:

  val oauth: OAuthClient[F] = OAuthClient[F](config.client)

  val keys: JwksSource[F] =
    config.client.endpoints.jwks match
      case Some(endpoint) => JwksSource.remote[F](endpoint, config.keysLifetime, config.keysCooldown)
      case None           => OidcClientImpl.missingKeys[F]

  def authorize(redirectUri: RedirectUri, options: AuthorizationOptions): F[AuthorizationFlow] =
    oauth.authorize(redirectUri, options.withScope(OidcClient.OpenId ++ options.scope.orElse(config.client.scope)).withNonce)

  def complete(flow: AuthorizationFlow, callback: AuthorizationCallback): F[OidcTokens] =
    F.flatMap(oauth.complete(flow, callback)) { response =>
      response.idToken match
        case None        => Requests.fail(OAuthError.MissingField("id_token"))
        case Some(token) =>
          F.flatMap(verify(token, flow.nonce)) { jwt =>
            F.map(Effect.fromEither(accessTokenHash(jwt, response.accessToken).left.map(_.toThrowable)))(_ => OidcTokens(response, jwt))
          }
    }

  def verify(idToken: IdToken, nonce: Option[Nonce]): F[Jwt] =
    val authorized = config.validation.withCheck(authorizedParty)

    JwtVerifier.verify[F](idToken.value, keys, nonce.fold(authorized)(expected => authorized.withNonce(expected.value)))

  def userInfo(token: AccessToken): F[UserInfo] =
    config.client.endpoints.userInfo match
      case None           => Requests.fail(OAuthError.MissingEndpoint("userinfo"))
      case Some(endpoint) => F.flatMap(oauth.resource(endpoint, token))(json => Requests.lift(UserInfo.fromJson(json)))

  def logoutUrl(idToken: Option[IdToken], redirectUri: Option[RedirectUri], state: Option[State]): Either[OAuthError, String] =
    config.client.endpoints.endSession.toRight(OAuthError.MissingEndpoint("end session")).map { endpoint =>
      val parameters =
        Vector("client_id" -> config.client.clientId.value) ++
          idToken.map(token => "id_token_hint" -> token.value) ++
          redirectUri.map(uri => "post_logout_redirect_uri" -> uri.value) ++
          state.map(value => "state" -> value.value)

      Urls.withQuery(endpoint.value, parameters)
    }

  private def authorizedParty(claims: JwtClaims): Either[JwtError, Unit] =
    val clientId = config.client.clientId.value

    claims.string("azp") match
      case Some(party) if party != clientId   => Left(JwtError.InvalidClaim("azp", s"the token was issued to '$party'"))
      case None if claims.audience.sizeIs > 1 => Left(JwtError.MissingClaim("azp"))
      case _                                  => Right(())

  private def accessTokenHash(jwt: Jwt, token: AccessToken): Either[JwtError, Unit] =
    (jwt.claims.string("at_hash"), JwsAlgorithm.parse(jwt.header.algorithm)) match
      case (Some(expected), Some(algorithm)) if algorithm.leftHash(token.value) != expected =>
        Left(JwtError.InvalidClaim("at_hash", "it does not match the access token it came with"))
      case _                                                                                => Right(())

private[oidc] object OidcClientImpl:

  private def missingKeys[F[*]](using F: Effect[F]): JwksSource[F] = new JwksSource[F]:
    def keys: F[Jwks]      = Requests.fail(OAuthError.MissingEndpoint("jwks"))
    def refreshed: F[Jwks] = Requests.fail(OAuthError.MissingEndpoint("jwks"))
