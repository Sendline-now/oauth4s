package oauth4s.internal

import java.util.Base64
import java.time.Instant
import java.nio.charset.StandardCharsets

import scala.concurrent.duration.{FiniteDuration, SECONDS}

import oauth4s.*
import oauth4s.json.{Json, JsonCodec}
import oauth4s.http.{HttpClient, HttpHeaders, HttpResponse}

private[oauth4s] final class OAuthClientImpl[F[*]](
    val config: OAuthClientConfig,
)(using F: Effect[F], http: HttpClient[F], codec: JsonCodec)
    extends OAuthClient[F]:

  private val agent = config.userAgent.fold(HttpHeaders.empty)(value => HttpHeaders.of("User-Agent" -> value))

  def authorize(redirectUri: RedirectUri, options: AuthorizationOptions): F[AuthorizationFlow] =
    config.endpoints.authorization match
      case None           => Requests.fail(OAuthError.MissingEndpoint("authorization"))
      case Some(endpoint) =>
        F.delay {
          val state    = State(Secrets.token(Secrets.StateBytes))
          val nonce    = Option.when(options.nonce)(Nonce(Secrets.token(Secrets.NonceBytes)))
          val verifier = options.pkce.map(_ => CodeVerifier.parse(Secrets.token(Secrets.VerifierBytes)).toOption.get)
          val scope    = options.scope.orElse(config.scope)

          val parameters =
            Vector("response_type" -> "code", "client_id" -> config.clientId.value, "redirect_uri" -> redirectUri.value, "state" -> state.value) ++
              Option.when(scope.nonEmpty)("scope" -> scope.render(config.scopeSeparator)) ++
              nonce.map(value => "nonce" -> value.value) ++
              pkceParameters(verifier, options.pkce) ++
              config.authorizationParameters ++
              options.parameters

          AuthorizationFlow(
            url = Urls.withQuery(endpoint.value, Urls.merge(parameters)),
            state = state,
            redirectUri = redirectUri,
            verifier = verifier,
            nonce = nonce,
          )
        }
  end authorize

  def complete(flow: AuthorizationFlow, callback: AuthorizationCallback): F[TokenResponse] =
    val code =
      for
        _    <- callback.error.toLeft(())
        _    <- Either.cond(callback.state.contains(flow.state), (), OAuthError.StateMismatch(flow.state, callback.state))
        _    <- issuerOf(callback)
        code <- callback.code.toRight(OAuthError.MissingField("code"))
      yield code

    val forwarded = config.forwardedCallbackParameters.flatMap(name => callback.parameter(name).map(name -> _))

    F.flatMap(Requests.lift(code))(exchangeCode(_, flow.redirectUri, flow.verifier, forwarded))
  end complete

  def exchangeCode(code: AuthorizationCode, redirectUri: RedirectUri, verifier: Option[CodeVerifier], parameters: Seq[(String, String)]): F[TokenResponse] =
    requestToken(
      GrantType.AuthorizationCode,
      Vector("code" -> code.value, "redirect_uri" -> redirectUri.value) ++ verifier.map(value => "code_verifier" -> value.value) ++ parameters,
    )

  def clientCredentials(scope: Scope): F[TokenResponse] =
    requestToken(GrantType.ClientCredentials, scopeParameter(scope.orElse(config.scope)))

  def refresh(token: RefreshToken, scope: Scope, parameters: Seq[(String, String)]): F[TokenResponse] =
    requestToken(GrantType.RefreshToken, Vector("refresh_token" -> token.value) ++ scopeParameter(scope) ++ parameters)

  def requestToken(grant: GrantType, parameters: Seq[(String, String)]): F[TokenResponse] =
    F.flatMap(attemptToken(grant, parameters))(Requests.lift)

  def deviceAuthorization(scope: Scope): F[DeviceAuthorization] =
    config.endpoints.deviceAuthorization match
      case None           => Requests.fail(OAuthError.MissingEndpoint("device authorization"))
      case Some(endpoint) =>
        F.flatMap(post(endpoint, scopeParameter(scope.orElse(config.scope)))) { (response, now) =>
          Requests.lift(Responses.decode(response)(DeviceAuthorization.fromJson(_, now)))
        }

  def awaitDevice(authorization: DeviceAuthorization): F[TokenResponse] = poll(authorization, authorization.interval)

  def revokeAccessToken(token: AccessToken): F[Unit] = revoke(token.value, TokenTypeHint.AccessToken)

  def revokeRefreshToken(token: RefreshToken): F[Unit] = revoke(token.value, TokenTypeHint.RefreshToken)

  def introspect(token: AccessToken): F[Introspection] =
    config.endpoints.introspection match
      case None           => Requests.fail(OAuthError.MissingEndpoint("introspection"))
      case Some(endpoint) =>
        F.flatMap(post(endpoint, Vector("token" -> token.value, "token_type_hint" -> TokenTypeHint.AccessToken.value))) { (response, _) =>
          Requests.lift(Responses.decode(response)(Introspection.fromJson))
        }

  def resource(endpoint: Endpoint, token: AccessToken, parameters: Seq[(String, String)]): F[Json] =
    Requests.getJson(endpoint.value, parameters, Requests.bearer(token) ++ agent)

  private def revoke(token: String, hint: TokenTypeHint): F[Unit] =
    config.endpoints.revocation match
      case None           => Requests.fail(OAuthError.MissingEndpoint("revocation"))
      case Some(endpoint) =>
        F.flatMap(post(endpoint, Vector("token" -> token, "token_type_hint" -> hint.value))) { (response, _) =>
          if response.isSuccess
          then F.unit
          else Requests.fail(Responses.error(response))
        }

  private def poll(authorization: DeviceAuthorization, interval: FiniteDuration): F[TokenResponse] =
    val parameters = Vector("device_code" -> authorization.deviceCode.value)

    F.flatMap(attemptToken(GrantType.DeviceCode, parameters)) {
      case Right(token)                                                    => F.pure(token)
      case Left(OAuthError.Rejected(ErrorCode.AuthorizationPending, _, _)) => pollAgain(authorization, interval)
      case Left(OAuthError.Rejected(ErrorCode.SlowDown, _, _))             => pollAgain(authorization, interval.plus(OAuthClientImpl.SlowDown))
      case Left(error)                                                     => Requests.fail(error)
    }

  private def pollAgain(authorization: DeviceAuthorization, interval: FiniteDuration): F[TokenResponse] =
    F.flatMap(F.realTime) { now =>
      if now.plusMillis(interval.toMillis).isBefore(authorization.expiresAt)
      then F.flatMap(F.sleep(interval))(_ => poll(authorization, interval))
      else Requests.fail(OAuthError.rejected(ErrorCode.ExpiredToken, "the device code expired before the user approved it"))
    }

  private def attemptToken(grant: GrantType, parameters: Seq[(String, String)]): F[Either[OAuthError, TokenResponse]] =
    F.map(post(config.endpoints.token, Vector("grant_type" -> grant.value) ++ parameters ++ config.tokenParameters)) { (response, now) =>
      Responses.decode(response)(json => TokenResponse.fromJson(config.tokenResponseAdapter(json), now))
    }

  private def post(endpoint: Endpoint, parameters: Seq[(String, String)]): F[(HttpResponse, Instant)] =
    F.flatMap(F.realTime) { sent =>
      val (headers, credentials) = authenticate(sent)

      F.flatMap(Requests.post(endpoint.value, Urls.merge(parameters.toVector ++ credentials), headers ++ agent)) { response =>
        F.map(F.realTime)(received => (response, received))
      }
    }

  private def authenticate(now: Instant): (HttpHeaders, Vector[(String, String)]) =
    def posted(secret: ClientSecret) = (HttpHeaders.empty, Vector("client_id" -> config.clientId.value, "client_secret" -> secret.value))

    config.authentication match
      case ClientAuthentication.Public              => (HttpHeaders.empty, Vector("client_id" -> config.clientId.value))
      case ClientAuthentication.SecretPost(secret)  => posted(secret)
      case ClientAuthentication.SecretMinted(mint)  => posted(mint(now))
      case ClientAuthentication.SecretBasic(secret) =>
        val pair = s"${Urls.formValue(config.clientId.value)}:${Urls.formValue(secret.value)}"

        (HttpHeaders.of("Authorization" -> s"Basic ${Base64.getEncoder.encodeToString(pair.getBytes(StandardCharsets.UTF_8))}"), Vector.empty)
  end authenticate

  private def issuerOf(callback: AuthorizationCallback): Either[OAuthError, Unit] =
    (config.issuer, callback.issuer) match
      case (Some(expected), Some(received)) if expected.value != received => Left(OAuthError.IssuerMismatch(expected, received))
      case _                                                              => Right(())

  private def pkceParameters(verifier: Option[CodeVerifier], method: Option[PkceMethod]): Vector[(String, String)] =
    verifier.zip(method).toVector.flatMap { (value, chosen) =>
      Vector("code_challenge" -> value.challenge(chosen).value, "code_challenge_method" -> chosen.value)
    }

  private def scopeParameter(scope: Scope): Vector[(String, String)] =
    if scope.isEmpty
    then Vector.empty
    else Vector("scope" -> scope.render(config.scopeSeparator))

private[oauth4s] object OAuthClientImpl:
  private val SlowDown = FiniteDuration(5, SECONDS)
