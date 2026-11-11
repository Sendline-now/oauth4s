package oauth4s.jwt

import oauth4s.internal.Base64Url
import oauth4s.json.{Json, JsonCodec}
import oauth4s.jwt.JwsAlgorithm.Family

enum JwkKey:
  case Rsa(modulus: BigInt, exponent: BigInt)
  case Ec(curve: String, x: BigInt, y: BigInt)
  case Symmetric(secret: Vector[Byte])
  case Unsupported(keyType: String)

  override def toString: String =
    this match
      case Rsa(modulus, _)      => s"Rsa(${modulus.bitLength} bits)"
      case Ec(curve, _, _)      => s"Ec($curve)"
      case Symmetric(_)         => "Symmetric"
      case Unsupported(keyType) => s"Unsupported($keyType)"

final case class Jwk(key: JwkKey, keyId: Option[String], algorithm: Option[String], use: Option[String]):
  def isSigningKey: Boolean = use.forall(_ == "sig")

  def supports(expected: JwsAlgorithm): Boolean =
    val fits = key match
      case JwkKey.Rsa(_, _)      => expected.family == Family.Rsa || expected.family == Family.Pss
      case JwkKey.Ec(_, _, _)    => expected.family == Family.Ec
      case JwkKey.Symmetric(_)   => expected.family == Family.Hmac
      case JwkKey.Unsupported(_) => false

    fits && algorithm.forall(_ == expected.name)

object Jwk:

  def fromJson(json: Json): Either[JwtError, Jwk] =
    for
      keyType <- json.string("kty").toRight(JwtError.InvalidKey("it has no 'kty'"))
      key     <- material(json, keyType)
    yield Jwk(key, json.string("kid"), json.string("alg"), json.string("use"))

  def symmetric(secret: Array[Byte], keyId: Option[String] = None): Jwk = Jwk(JwkKey.Symmetric(secret.toVector), keyId, None, Some("sig"))

  private def material(json: Json, keyType: String): Either[JwtError, JwkKey] =
    keyType match
      case "RSA" =>
        for
          modulus  <- number(json, "n")
          exponent <- number(json, "e")
        yield JwkKey.Rsa(modulus, exponent)
      case "EC"  =>
        for
          curve <- json.string("crv").toRight(JwtError.InvalidKey("an EC key needs 'crv'"))
          x     <- number(json, "x")
          y     <- number(json, "y")
        yield JwkKey.Ec(curve, x, y)
      case "oct" => bytes(json, "k").map(secret => JwkKey.Symmetric(secret.toVector))
      case other => Right(JwkKey.Unsupported(other))

  private def number(json: Json, name: String): Either[JwtError, BigInt] = bytes(json, name).map(BigInt(1, _))

  private def bytes(json: Json, name: String): Either[JwtError, Array[Byte]] =
    json.string(name).flatMap(Base64Url.decode).toRight(JwtError.InvalidKey(s"'$name' is missing or is not base64url"))

final case class Jwks(keys: Vector[Jwk]):
  def find(keyId: String): Option[Jwk] = keys.find(_.keyId.contains(keyId))

  def candidates(keyId: Option[String], algorithm: JwsAlgorithm): Vector[Jwk] =
    val usable = keys.filter(key => key.isSigningKey && key.supports(algorithm))

    keyId.fold(usable)(id => usable.filter(_.keyId.contains(id)))

object Jwks:
  val empty: Jwks = Jwks(Vector.empty)

  def of(keys: Jwk*): Jwks = Jwks(keys.toVector)

  def fromJson(json: Json): Either[JwtError, Jwks] =
    json.array("keys").toRight(JwtError.Malformed("a key set needs a 'keys' array")).map(values => Jwks(values.flatMap(Jwk.fromJson(_).toOption)))

  def parse(text: String)(using codec: JsonCodec): Either[JwtError, Jwks] =
    codec.parse(text).left.map(error => JwtError.Malformed(s"the key set is not JSON: ${error.message}")).flatMap(fromJson)
