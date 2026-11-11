package oauth4s

import java.time.Instant

import scala.concurrent.duration.{FiniteDuration, SECONDS}

import oauth4s.json.Json
import oauth4s.internal.Fields

final case class DeviceAuthorization(
    deviceCode: DeviceCode,
    userCode: UserCode,
    verificationUri: String,
    verificationUriComplete: Option[String],
    expiresIn: FiniteDuration,
    interval: FiniteDuration,
    issuedAt: Instant,
    raw: Json,
):
  def expiresAt: Instant = issuedAt.plusMillis(expiresIn.toMillis)

object DeviceAuthorization:
  val DefaultInterval: FiniteDuration = FiniteDuration(5, SECONDS)
  val DefaultLifetime: FiniteDuration = FiniteDuration(600, SECONDS)

  def fromJson(json: Json, issuedAt: Instant): Either[OAuthError, DeviceAuthorization] =
    for
      deviceCode <- Fields.required(json, "device_code")
      userCode   <- Fields.required(json, "user_code")
      uri        <- Fields.optional(json, "verification_uri").orElse(Fields.optional(json, "verification_url")).toRight(OAuthError.MissingField("verification_uri"))
      expiresIn  <- Fields.seconds(json, "expires_in")
      interval   <- Fields.seconds(json, "interval")
    yield DeviceAuthorization(
      deviceCode = DeviceCode(deviceCode),
      userCode = UserCode(userCode),
      verificationUri = uri,
      verificationUriComplete = Fields.optional(json, "verification_uri_complete"),
      expiresIn = expiresIn.getOrElse(DefaultLifetime),
      interval = interval.filter(_.toMillis > 0).getOrElse(DefaultInterval),
      issuedAt = issuedAt,
      raw = json,
    )
