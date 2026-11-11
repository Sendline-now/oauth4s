package oauth4s.jwt

import java.time.Instant

enum JwtError:
  case Malformed(reason: String)
  case UnsupportedAlgorithm(algorithm: String)
  case AlgorithmNotAllowed(algorithm: JwsAlgorithm)
  case UnsupportedCritical(headers: Vector[String])
  case UnknownKey(keyId: Option[String])
  case InvalidKey(reason: String)
  case InvalidSignature
  case Expired(at: Instant)
  case NotYetValid(from: Instant)
  case IssuerMismatch(expected: Set[String], received: Option[String])
  case AudienceMismatch(expected: String, received: Vector[String])
  case NonceMismatch
  case MissingClaim(name: String)
  case InvalidClaim(name: String, reason: String)

  def message: String =
    this match
      case Malformed(reason)                    => s"the token is malformed: $reason"
      case UnsupportedAlgorithm(algorithm)      => s"the algorithm '$algorithm' is not supported"
      case AlgorithmNotAllowed(algorithm)       => s"the algorithm ${algorithm.name} is not among the ones this validation accepts"
      case UnsupportedCritical(headers)         => s"the token requires extensions this library does not implement: ${headers.mkString(", ")}"
      case UnknownKey(keyId)                    => s"no key in the set matches ${keyId.fold("the token")(id => s"key id '$id'")}"
      case InvalidKey(reason)                   => s"the key cannot be used: $reason"
      case InvalidSignature                     => "the signature does not verify"
      case Expired(at)                          => s"the token expired at $at"
      case NotYetValid(from)                    => s"the token is not valid before $from"
      case IssuerMismatch(expected, received)   =>
        s"the token comes from ${received.fold("no issuer")(issuer => s"'$issuer'")}, not from ${expected.mkString("'", "' or '", "'")}"
      case AudienceMismatch(expected, received) => s"the token is meant for ${received.mkString("'", "', '", "'")}, not for '$expected'"
      case NonceMismatch                        => "the token does not carry the nonce the flow was started with"
      case MissingClaim(name)                   => s"the claim '$name' is missing"
      case InvalidClaim(name, reason)           => s"the claim '$name' is invalid: $reason"

  def toThrowable: JwtException = JwtException(this)

final class JwtException(val error: JwtError) extends RuntimeException(error.message)
