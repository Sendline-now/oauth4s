package oauth4s.oidc

import scala.concurrent.duration.FiniteDuration

import oauth4s.jwt.{JwksSource, JwtValidation}
import oauth4s.{Issuer, OAuthClientConfig, OAuthError, ServerMetadata}

final class OidcConfig private (
    val client: OAuthClientConfig,
    val issuer: Issuer,
    val validation: JwtValidation,
    val keysLifetime: FiniteDuration,
    val keysCooldown: FiniteDuration,
):
  def withClient(adjust: OAuthClientConfig => OAuthClientConfig): OidcConfig = copy(client = adjust(client))

  def withValidation(adjust: JwtValidation => JwtValidation): OidcConfig = copy(validation = adjust(validation))

  def withKeysLifetime(value: FiniteDuration): OidcConfig = copy(keysLifetime = value)

  def withKeysCooldown(value: FiniteDuration): OidcConfig = copy(keysCooldown = value)

  private def copy(
      client: OAuthClientConfig = client,
      validation: JwtValidation = validation,
      keysLifetime: FiniteDuration = keysLifetime,
      keysCooldown: FiniteDuration = keysCooldown,
  ): OidcConfig =
    new OidcConfig(
      client = client,
      issuer = issuer,
      validation = validation,
      keysLifetime = keysLifetime,
      keysCooldown = keysCooldown,
    )

object OidcConfig:

  def apply(client: OAuthClientConfig, issuer: Issuer): OidcConfig =
    new OidcConfig(
      client = client.withIssuer(issuer),
      issuer = issuer,
      validation = JwtValidation.of(issuer.value, client.clientId.value),
      keysLifetime = JwksSource.DefaultLifetime,
      keysCooldown = JwksSource.DefaultCooldown,
    )

  def fromMetadata(metadata: ServerMetadata, client: OAuthClientConfig): Either[OAuthError, OidcConfig] =
    metadata.jwksUri.toRight(OAuthError.MissingField("jwks_uri")).map(_ => apply(client, metadata.issuer))
