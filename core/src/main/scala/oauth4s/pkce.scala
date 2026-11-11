package oauth4s

import java.security.MessageDigest
import java.nio.charset.StandardCharsets

import oauth4s.internal.{Base64Url, Secrets}

enum PkceMethod(val value: String):
  case S256  extends PkceMethod("S256")
  case Plain extends PkceMethod("plain")

object PkceMethod:
  def parse(raw: String): Option[PkceMethod] = values.find(_.value == raw)

opaque type CodeVerifier = String

object CodeVerifier:
  val MinLength: Int = 43
  val MaxLength: Int = 128

  def parse(raw: String): Either[OAuthError, CodeVerifier] =
    if raw.length < MinLength || raw.length > MaxLength
    then Left(OAuthError.InvalidCodeVerifier(s"it must be between $MinLength and $MaxLength characters long"))
    else if !raw.forall(Secrets.isUnreserved)
    then Left(OAuthError.InvalidCodeVerifier("it may only contain unreserved URI characters"))
    else Right(raw)

  def generate[F[*]](using F: Effect[F]): F[CodeVerifier] = F.delay(Secrets.token(Secrets.VerifierBytes))

  extension (verifier: CodeVerifier)
    inline def value: String                         = verifier
    def challenge(method: PkceMethod): CodeChallenge = CodeChallenge.of(verifier, method)

opaque type CodeChallenge = String

object CodeChallenge:
  def of(verifier: CodeVerifier, method: PkceMethod): CodeChallenge =
    method match
      case PkceMethod.Plain => verifier.value
      case PkceMethod.S256  => Base64Url.encode(MessageDigest.getInstance("SHA-256").digest(verifier.value.getBytes(StandardCharsets.US_ASCII)))

  extension (challenge: CodeChallenge) inline def value: String = challenge
