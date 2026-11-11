package oauth4s

final class Endpoints private (
    val token: Endpoint,
    val authorization: Option[Endpoint],
    val deviceAuthorization: Option[Endpoint],
    val revocation: Option[Endpoint],
    val introspection: Option[Endpoint],
    val userInfo: Option[Endpoint],
    val jwks: Option[Endpoint],
    val endSession: Option[Endpoint],
):
  def withAuthorization(endpoint: Endpoint): Endpoints = copy(authorization = Some(endpoint))

  def withDeviceAuthorization(endpoint: Endpoint): Endpoints = copy(deviceAuthorization = Some(endpoint))

  def withRevocation(endpoint: Endpoint): Endpoints = copy(revocation = Some(endpoint))

  def withIntrospection(endpoint: Endpoint): Endpoints = copy(introspection = Some(endpoint))

  def withUserInfo(endpoint: Endpoint): Endpoints = copy(userInfo = Some(endpoint))

  def withJwks(endpoint: Endpoint): Endpoints = copy(jwks = Some(endpoint))

  def withEndSession(endpoint: Endpoint): Endpoints = copy(endSession = Some(endpoint))

  override def toString: String = s"Endpoints(token = ${token.value}, authorization = ${authorization.map(_.value)})"

  private def copy(
      authorization: Option[Endpoint] = authorization,
      deviceAuthorization: Option[Endpoint] = deviceAuthorization,
      revocation: Option[Endpoint] = revocation,
      introspection: Option[Endpoint] = introspection,
      userInfo: Option[Endpoint] = userInfo,
      jwks: Option[Endpoint] = jwks,
      endSession: Option[Endpoint] = endSession,
  ): Endpoints =
    new Endpoints(
      token = token,
      authorization = authorization,
      deviceAuthorization = deviceAuthorization,
      revocation = revocation,
      introspection = introspection,
      userInfo = userInfo,
      jwks = jwks,
      endSession = endSession,
    )

object Endpoints:

  def of(token: Endpoint): Endpoints =
    new Endpoints(
      token = token,
      authorization = None,
      deviceAuthorization = None,
      revocation = None,
      introspection = None,
      userInfo = None,
      jwks = None,
      endSession = None,
    )

  def of(authorization: Endpoint, token: Endpoint): Endpoints = of(token).withAuthorization(authorization)

  def fromMetadata(metadata: ServerMetadata): Either[OAuthError, Endpoints] =
    metadata.tokenEndpoint.toRight(OAuthError.MissingField("token_endpoint")).map { token =>
      new Endpoints(
        token = token,
        authorization = metadata.authorizationEndpoint,
        deviceAuthorization = metadata.deviceAuthorizationEndpoint,
        revocation = metadata.revocationEndpoint,
        introspection = metadata.introspectionEndpoint,
        userInfo = metadata.userInfoEndpoint,
        jwks = metadata.jwksUri,
        endSession = metadata.endSessionEndpoint,
      )
    }
