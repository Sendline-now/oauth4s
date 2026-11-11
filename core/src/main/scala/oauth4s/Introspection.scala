package oauth4s

import java.time.Instant

import oauth4s.json.Json
import oauth4s.internal.Fields

final case class Introspection(
    active: Boolean,
    scope: Option[Scope],
    clientId: Option[ClientId],
    username: Option[String],
    tokenType: Option[TokenType],
    expiresAt: Option[Instant],
    issuedAt: Option[Instant],
    notBefore: Option[Instant],
    subject: Option[String],
    audience: Vector[String],
    issuer: Option[String],
    tokenId: Option[String],
    raw: Json,
)

object Introspection:

  def fromJson(json: Json): Either[OAuthError, Introspection] =
    json.boolean("active").toRight(OAuthError.MissingField("active")).map { active =>
      Introspection(
        active = active,
        scope = Fields.optional(json, "scope").map(Scope.parse),
        clientId = Fields.optional(json, "client_id").map(ClientId(_)),
        username = Fields.optional(json, "username"),
        tokenType = Fields.optional(json, "token_type").map(TokenType.parse),
        expiresAt = Fields.instant(json, "exp"),
        issuedAt = Fields.instant(json, "iat"),
        notBefore = Fields.instant(json, "nbf"),
        subject = Fields.optional(json, "sub"),
        audience = Fields.stringOrStrings(json, "aud"),
        issuer = Fields.optional(json, "iss"),
        tokenId = Fields.optional(json, "jti"),
        raw = json,
      )
    }
