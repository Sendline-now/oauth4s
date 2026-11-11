package oauth4s.jwt

import java.time.Instant
import java.nio.charset.StandardCharsets

import oauth4s.Effect
import oauth4s.json.JsonCodec
import oauth4s.jwt.internal.Signatures

object JwtVerifier:

  def verify(token: String, keys: Jwks, validation: JwtValidation, now: Instant)(using JsonCodec): Either[JwtError, Jwt] =
    for
      jwt       <- Jwt.parse(token)
      algorithm <- JwsAlgorithm.parse(jwt.header.algorithm).toRight(JwtError.UnsupportedAlgorithm(jwt.header.algorithm))
      _         <- Either.cond(validation.algorithms.contains(algorithm), (), JwtError.AlgorithmNotAllowed(algorithm))
      _         <- Either.cond(jwt.header.critical.isEmpty, (), JwtError.UnsupportedCritical(jwt.header.critical))
      _         <- signed(jwt, algorithm, keys)
      _         <- claimed(jwt.claims, validation, now)
    yield jwt

  def verify[F[*]](token: String, keys: JwksSource[F], validation: JwtValidation)(using F: Effect[F], codec: JsonCodec): F[Jwt] =
    def attempt(source: F[Jwks]): F[Either[JwtError, Jwt]] =
      F.flatMap(source)(current => F.map(F.realTime)(now => verify(token, current, validation, now)))

    F.flatMap(attempt(keys.keys)) {
      case Left(JwtError.UnknownKey(_)) => F.flatMap(attempt(keys.refreshed))(lift)
      case other                        => lift(other)
    }
  end verify

  private def lift[F[*]](result: Either[JwtError, Jwt])(using F: Effect[F]): F[Jwt] =
    result.fold(error => F.raiseError(error.toThrowable), F.pure)

  private def signed(jwt: Jwt, algorithm: JwsAlgorithm, keys: Jwks): Either[JwtError, Unit] =
    val input     = jwt.signingInput.getBytes(StandardCharsets.US_ASCII)
    val provided  = jwt.signature.toArray
    val candidate = keys.candidates(jwt.header.keyId, algorithm)

    if candidate.isEmpty
    then Left(JwtError.UnknownKey(jwt.header.keyId))
    else if candidate.exists(key => Signatures.verify(algorithm, key.key, input, provided).contains(true))
    then Right(())
    else Left(JwtError.InvalidSignature)
  end signed

  private def claimed(claims: JwtClaims, validation: JwtValidation, now: Instant): Either[JwtError, Unit] =
    val leeway = validation.leeway.toMillis

    for
      _ <- claims.expiresAt match
             case None if validation.requiresExpiry                        => Left(JwtError.MissingClaim("exp"))
             case Some(expiry) if !now.isBefore(expiry.plusMillis(leeway)) => Left(JwtError.Expired(expiry))
             case _                                                        => Right(())
      _ <- claims.notBefore.filter(from => now.isBefore(from.minusMillis(leeway))).map(JwtError.NotYetValid(_)).toLeft(())
      _ <- Either.cond(validation.issuers.isEmpty || claims.issuer.exists(validation.issuers), (), JwtError.IssuerMismatch(validation.issuers, claims.issuer))
      _ <- validation.audience.filterNot(claims.audience.contains).map(JwtError.AudienceMismatch(_, claims.audience)).toLeft(())
      _ <- validation.checks.foldLeft[Either[JwtError, Unit]](Right(()))((result, check) => result.flatMap(_ => check(claims)))
    yield ()
  end claimed
