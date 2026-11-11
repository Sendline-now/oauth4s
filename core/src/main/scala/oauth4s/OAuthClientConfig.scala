package oauth4s

import oauth4s.json.Json

final class OAuthClientConfig private (
    val clientId: ClientId,
    val authentication: ClientAuthentication,
    val endpoints: Endpoints,
    val issuer: Option[Issuer],
    val scope: Scope,
    val scopeSeparator: String,
    val authorizationParameters: Vector[(String, String)],
    val tokenParameters: Vector[(String, String)],
    val forwardedCallbackParameters: Vector[String],
    val tokenResponseAdapter: Json => Json,
    val userAgent: Option[String],
):
  def withIssuer(value: Issuer): OAuthClientConfig = copy(issuer = Some(value))

  def withScope(value: Scope): OAuthClientConfig = copy(scope = value)

  def withScopeSeparator(value: String): OAuthClientConfig =
    require(value.nonEmpty, "the scope separator must not be empty — RFC 6749 uses a space, and some providers a comma")
    copy(scopeSeparator = value)
  end withScopeSeparator

  def withAuthorizationParameter(name: String, value: String): OAuthClientConfig = copy(authorizationParameters = authorizationParameters :+ (name -> value))

  def withTokenParameter(name: String, value: String): OAuthClientConfig = copy(tokenParameters = tokenParameters :+ (name -> value))

  def forwardingCallbackParameter(name: String): OAuthClientConfig = copy(forwardedCallbackParameters = forwardedCallbackParameters :+ name)

  def adaptingTokenResponse(adapter: Json => Json): OAuthClientConfig = copy(tokenResponseAdapter = adapter)

  def withUserAgent(value: String): OAuthClientConfig = copy(userAgent = Some(value))

  override def toString: String =
    s"OAuthClientConfig(clientId = ${clientId.value}, authentication = ${authentication.method}, endpoints = $endpoints, scope = ${scope.render})"

  private def copy(
      issuer: Option[Issuer] = issuer,
      scope: Scope = scope,
      scopeSeparator: String = scopeSeparator,
      authorizationParameters: Vector[(String, String)] = authorizationParameters,
      tokenParameters: Vector[(String, String)] = tokenParameters,
      forwardedCallbackParameters: Vector[String] = forwardedCallbackParameters,
      tokenResponseAdapter: Json => Json = tokenResponseAdapter,
      userAgent: Option[String] = userAgent,
  ): OAuthClientConfig =
    new OAuthClientConfig(
      clientId = clientId,
      authentication = authentication,
      endpoints = endpoints,
      issuer = issuer,
      scope = scope,
      scopeSeparator = scopeSeparator,
      authorizationParameters = authorizationParameters,
      tokenParameters = tokenParameters,
      forwardedCallbackParameters = forwardedCallbackParameters,
      tokenResponseAdapter = tokenResponseAdapter,
      userAgent = userAgent,
    )

object OAuthClientConfig:

  def apply(clientId: ClientId, authentication: ClientAuthentication, endpoints: Endpoints): OAuthClientConfig =
    new OAuthClientConfig(
      clientId = clientId,
      authentication = authentication,
      endpoints = endpoints,
      issuer = None,
      scope = Scope.empty,
      scopeSeparator = Scope.Separator,
      authorizationParameters = Vector.empty,
      tokenParameters = Vector.empty,
      forwardedCallbackParameters = Vector.empty,
      tokenResponseAdapter = json => json,
      userAgent = None,
    )

  def fromMetadata(metadata: ServerMetadata, clientId: ClientId, authentication: ClientAuthentication): Either[OAuthError, OAuthClientConfig] =
    Endpoints.fromMetadata(metadata).map(endpoints => apply(clientId, authentication, endpoints).withIssuer(metadata.issuer))
