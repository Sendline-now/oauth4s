package oauth4s.jwt

import scala.concurrent.duration.{FiniteDuration, SECONDS}

final class JwtValidation private (
    val issuers: Set[String],
    val audience: Option[String],
    val algorithms: Set[JwsAlgorithm],
    val leeway: FiniteDuration,
    val requiresExpiry: Boolean,
    val checks: Vector[JwtClaims => Either[JwtError, Unit]],
):
  def withIssuer(accepted: String*): JwtValidation = copy(issuers = accepted.toSet)

  def withAudience(value: String): JwtValidation = copy(audience = Some(value))

  def withAlgorithms(accepted: JwsAlgorithm*): JwtValidation =
    require(accepted.nonEmpty, "a validation that accepts no algorithm rejects every token — name at least one")
    copy(algorithms = accepted.toSet)
  end withAlgorithms

  def withLeeway(value: FiniteDuration): JwtValidation = copy(leeway = value)

  def withoutExpiry: JwtValidation = copy(requiresExpiry = false)

  def withCheck(check: JwtClaims => Either[JwtError, Unit]): JwtValidation = copy(checks = checks :+ check)

  def withNonce(expected: String): JwtValidation =
    withCheck(claims => Either.cond(claims.nonce.contains(expected), (), JwtError.NonceMismatch))

  private def copy(
      issuers: Set[String] = issuers,
      audience: Option[String] = audience,
      algorithms: Set[JwsAlgorithm] = algorithms,
      leeway: FiniteDuration = leeway,
      requiresExpiry: Boolean = requiresExpiry,
      checks: Vector[JwtClaims => Either[JwtError, Unit]] = checks,
  ): JwtValidation =
    new JwtValidation(
      issuers = issuers,
      audience = audience,
      algorithms = algorithms,
      leeway = leeway,
      requiresExpiry = requiresExpiry,
      checks = checks,
    )

object JwtValidation:
  val default: JwtValidation =
    new JwtValidation(
      issuers = Set.empty,
      audience = None,
      algorithms = JwsAlgorithm.Asymmetric,
      leeway = FiniteDuration(60, SECONDS),
      requiresExpiry = true,
      checks = Vector.empty,
    )

  def of(issuer: String, audience: String): JwtValidation = default.withIssuer(issuer).withAudience(audience)
