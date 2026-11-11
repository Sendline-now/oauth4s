package oauth4s

import oauth4s.json.Json
import oauth4s.internal.Fields

final case class ServerMetadata(
    issuer: Issuer,
    authorizationEndpoint: Option[Endpoint],
    tokenEndpoint: Option[Endpoint],
    deviceAuthorizationEndpoint: Option[Endpoint],
    revocationEndpoint: Option[Endpoint],
    introspectionEndpoint: Option[Endpoint],
    userInfoEndpoint: Option[Endpoint],
    jwksUri: Option[Endpoint],
    endSessionEndpoint: Option[Endpoint],
    scopesSupported: Vector[String],
    grantTypesSupported: Vector[String],
    responseTypesSupported: Vector[String],
    codeChallengeMethodsSupported: Vector[String],
    tokenEndpointAuthMethodsSupported: Vector[String],
    idTokenSigningAlgValuesSupported: Vector[String],
    raw: Json,
):
  def supportsPkce: Boolean = codeChallengeMethodsSupported.contains(PkceMethod.S256.value)

  def supportsGrant(grant: GrantType): Boolean = grantTypesSupported.isEmpty || grantTypesSupported.contains(grant.value)

  def supportsScope(token: String): Boolean = scopesSupported.isEmpty || scopesSupported.contains(token)

object ServerMetadata:

  def fromJson(json: Json): Either[OAuthError, ServerMetadata] =
    for
      issuer              <- Fields.required(json, "issuer")
      authorization       <- Fields.endpoint(json, "authorization_endpoint")
      token               <- Fields.endpoint(json, "token_endpoint")
      deviceAuthorization <- Fields.endpoint(json, "device_authorization_endpoint")
      revocation          <- Fields.endpoint(json, "revocation_endpoint")
      introspection       <- Fields.endpoint(json, "introspection_endpoint")
      userInfo            <- Fields.endpoint(json, "userinfo_endpoint")
      jwks                <- Fields.endpoint(json, "jwks_uri")
      endSession          <- Fields.endpoint(json, "end_session_endpoint")
    yield ServerMetadata(
      issuer = Issuer(issuer),
      authorizationEndpoint = authorization,
      tokenEndpoint = token,
      deviceAuthorizationEndpoint = deviceAuthorization,
      revocationEndpoint = revocation,
      introspectionEndpoint = introspection,
      userInfoEndpoint = userInfo,
      jwksUri = jwks,
      endSessionEndpoint = endSession,
      scopesSupported = json.strings("scopes_supported"),
      grantTypesSupported = json.strings("grant_types_supported"),
      responseTypesSupported = json.strings("response_types_supported"),
      codeChallengeMethodsSupported = json.strings("code_challenge_methods_supported"),
      tokenEndpointAuthMethodsSupported = json.strings("token_endpoint_auth_methods_supported"),
      idTokenSigningAlgValuesSupported = json.strings("id_token_signing_alg_values_supported"),
      raw = json,
    )
