package oauth4s

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

import oauth4s.json.Json
import oauth4s.internal.Fields

final case class TokenResponse(
    accessToken: AccessToken,
    tokenType: TokenType,
    expiresIn: Option[FiniteDuration],
    refreshToken: Option[RefreshToken],
    scope: Option[Scope],
    idToken: Option[IdToken],
    issuedAt: Instant,
    raw: Json,
):
  def expiresAt: Option[Instant] = expiresIn.map(lifetime => issuedAt.plusMillis(lifetime.toMillis))

  def expiresWithin(leeway: FiniteDuration, now: Instant): Boolean = expiresAt.exists(expiry => !expiry.isAfter(now.plusMillis(leeway.toMillis)))

  def isExpired(now: Instant): Boolean = expiresAt.exists(expiry => !expiry.isAfter(now))

  def toJson: Json =
    Json.Obj(
      Vector(
        Some("access_token" -> Json.str(accessToken.value)),
        Some("token_type"   -> Json.str(tokenType.value)),
        expiresIn.map(lifetime => "expires_in" -> Json.num(lifetime.toSeconds)),
        refreshToken.map(token => "refresh_token" -> Json.str(token.value)),
        scope.map(granted => "scope" -> Json.str(granted.render)),
        idToken.map(token => "id_token" -> Json.str(token.value)),
        Some("issued_at"    -> Json.num(issuedAt.getEpochSecond)),
      ).flatten
    )

  override def toString: String =
    s"TokenResponse(tokenType = ${tokenType.value}, expiresIn = $expiresIn, scope = ${scope.map(_.render)}, refreshable = ${refreshToken.isDefined}, issuedAt = $issuedAt)"

object TokenResponse:

  def fromJson(json: Json, issuedAt: Instant): Either[OAuthError, TokenResponse] =
    for
      accessToken <- Fields.required(json, "access_token")
      expiresIn   <- Fields.seconds(json, "expires_in")
    yield TokenResponse(
      accessToken = AccessToken(accessToken),
      tokenType = json.string("token_type").fold(TokenType.Bearer)(TokenType.parse),
      expiresIn = expiresIn,
      refreshToken = Fields.optional(json, "refresh_token").map(RefreshToken(_)),
      scope = Fields.optional(json, "scope").map(Scope.parse),
      idToken = Fields.optional(json, "id_token").map(IdToken(_)),
      issuedAt = issuedAt,
      raw = json,
    )

  def fromJson(json: Json): Either[OAuthError, TokenResponse] =
    Fields.instant(json, "issued_at").toRight(OAuthError.MissingField("issued_at")).flatMap(fromJson(json, _))
