package oauth4s.jwt

import java.time.Instant

import oauth4s.internal.{Base64Url, Fields}
import oauth4s.json.{Json, JsonCodec, JsonDecoder, JsonError}

final case class JwtHeader(
    algorithm: String,
    keyId: Option[String],
    tokenType: Option[String],
    critical: Vector[String],
    raw: Json,
)

opaque type JwtClaims = Json

object JwtClaims:
  def apply(json: Json): JwtClaims = json

  extension (claims: JwtClaims)
    inline def raw: Json                                               = claims
    def claim(name: String): Option[Json]                              = claims.field(name)
    def string(name: String): Option[String]                           = claims.string(name)
    def flag(name: String): Option[Boolean]                            = claims.boolean(name).orElse(claims.string(name).flatMap(_.toBooleanOption))
    def instant(name: String): Option[Instant]                         = Fields.instant(claims, name)
    def issuer: Option[String]                                         = claims.string("iss")
    def subject: Option[String]                                        = claims.string("sub")
    def audience: Vector[String]                                       = Fields.stringOrStrings(claims, "aud")
    def expiresAt: Option[Instant]                                     = Fields.instant(claims, "exp")
    def notBefore: Option[Instant]                                     = Fields.instant(claims, "nbf")
    def issuedAt: Option[Instant]                                      = Fields.instant(claims, "iat")
    def tokenId: Option[String]                                        = claims.string("jti")
    def nonce: Option[String]                                          = claims.string("nonce")
    def decode[A](using decoder: JsonDecoder[A]): Either[JsonError, A] = decoder.decode(claims)

final case class Jwt(header: JwtHeader, claims: JwtClaims, signature: Vector[Byte], signingInput: String)

object Jwt:

  def parse(token: String)(using JsonCodec): Either[JwtError, Jwt] =
    token.trim.split("\\.", -1) match
      case Array(headerPart, claimsPart, signaturePart) =>
        for
          header    <- part(headerPart, "header")
          claims    <- part(claimsPart, "claims")
          algorithm <- header.string("alg").toRight(JwtError.Malformed("the header has no 'alg'"))
          signature <- Base64Url.decode(signaturePart).toRight(JwtError.Malformed("the signature is not base64url"))
        yield Jwt(
          header = JwtHeader(algorithm, header.string("kid"), header.string("typ"), header.strings("crit"), header),
          claims = JwtClaims(claims),
          signature = signature.toVector,
          signingInput = s"$headerPart.$claimsPart",
        )
      case parts                                        => Left(JwtError.Malformed(s"a signed token has three parts, this one has ${parts.length}"))

  private def part(encoded: String, name: String)(using codec: JsonCodec): Either[JwtError, Json] =
    for
      text <- Base64Url.decodeText(encoded).toRight(JwtError.Malformed(s"the $name is not base64url"))
      json <- codec.parse(text).left.map(error => JwtError.Malformed(s"the $name is not JSON: ${error.message}"))
      _    <- json.asObject.toRight(JwtError.Malformed(s"the $name is not a JSON object"))
    yield json
