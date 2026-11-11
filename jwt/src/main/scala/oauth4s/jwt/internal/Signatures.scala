package oauth4s.jwt.internal

import javax.crypto.{Mac, SecretKey}
import javax.crypto.spec.SecretKeySpec
import java.security.{AlgorithmParameters, GeneralSecurityException, Key, KeyFactory, MessageDigest, PrivateKey, PublicKey, Signature}
import java.security.spec.{ECGenParameterSpec, ECParameterSpec, ECPoint, ECPublicKeySpec, MGF1ParameterSpec, PSSParameterSpec, RSAPublicKeySpec}

import oauth4s.jwt.JwsAlgorithm.Family
import oauth4s.jwt.{JwkKey, JwsAlgorithm, JwtError}

private[jwt] object Signatures:

  def verify(algorithm: JwsAlgorithm, key: JwkKey, input: Array[Byte], provided: Array[Byte]): Either[JwtError, Boolean] =
    key match
      case JwkKey.Symmetric(secret) if algorithm.family == Family.Hmac =>
        attempt(MessageDigest.isEqual(mac(algorithm, SecretKeySpec(secret.toArray, algorithm.jca), input), provided))
      case JwkKey.Rsa(modulus, exponent) if isRsa(algorithm)           => rsaKey(modulus, exponent).map(verifyWith(algorithm, _, input, provided))
      case JwkKey.Ec(curve, x, y) if algorithm.family == Family.Ec     =>
        if provided.length == 2 * coordinateBytes(algorithm)
        then ecKey(algorithm, curve, x, y).map(verifyWith(algorithm, _, input, provided))
        else Right(false)
      case _                                                           => Left(JwtError.InvalidKey(s"it is not a key for ${algorithm.name}"))

  def sign(algorithm: JwsAlgorithm, key: Key, input: Array[Byte]): Either[JwtError, Array[Byte]] =
    key match
      case secret: SecretKey if algorithm.family == Family.Hmac   => attempt(mac(algorithm, secret, input))
      case signing: PrivateKey if algorithm.family != Family.Hmac =>
        attempt {
          val signer = signature(algorithm)
          signer.initSign(signing)
          signer.update(input)
          signer.sign()
        }
      case _                                                      => Left(JwtError.InvalidKey(s"it is not a signing key for ${algorithm.name}"))

  def hash(algorithm: JwsAlgorithm, input: Array[Byte]): Array[Byte] = MessageDigest.getInstance(algorithm.digest).digest(input)

  private def verifyWith(algorithm: JwsAlgorithm, key: PublicKey, input: Array[Byte], provided: Array[Byte]): Boolean =
    try
      val verifier = signature(algorithm)
      verifier.initVerify(key)
      verifier.update(input)
      verifier.verify(provided)
    catch case _: GeneralSecurityException => false

  private def signature(algorithm: JwsAlgorithm): Signature =
    val instance = Signature.getInstance(algorithm.jca)

    if algorithm.family == Family.Pss then
      instance.setParameter(
        PSSParameterSpec(algorithm.digest, "MGF1", MGF1ParameterSpec(algorithm.digest), MessageDigest.getInstance(algorithm.digest).getDigestLength, 1)
      )

    instance

  private def mac(algorithm: JwsAlgorithm, key: SecretKey, input: Array[Byte]): Array[Byte] =
    val instance = Mac.getInstance(algorithm.jca)
    instance.init(key)
    instance.doFinal(input)

  private def rsaKey(modulus: BigInt, exponent: BigInt): Either[JwtError, PublicKey] =
    attempt(KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus.bigInteger, exponent.bigInteger)))

  private def ecKey(algorithm: JwsAlgorithm, curve: String, x: BigInt, y: BigInt): Either[JwtError, PublicKey] =
    val (expected, standard) = curveOf(algorithm)

    if expected == curve
    then
      attempt {
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(ECGenParameterSpec(standard))

        KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x.bigInteger, y.bigInteger), parameters.getParameterSpec(classOf[ECParameterSpec])))
      }
    else Left(JwtError.InvalidKey(s"${algorithm.name} needs a key on $expected, this one is on $curve"))

  private def curveOf(algorithm: JwsAlgorithm): (String, String) =
    algorithm match
      case JwsAlgorithm.ES384 => ("P-384", "secp384r1")
      case JwsAlgorithm.ES512 => ("P-521", "secp521r1")
      case _                  => ("P-256", "secp256r1")

  private def coordinateBytes(algorithm: JwsAlgorithm): Int =
    algorithm match
      case JwsAlgorithm.ES384 => 48
      case JwsAlgorithm.ES512 => 66
      case _                  => 32

  private def isRsa(algorithm: JwsAlgorithm): Boolean = algorithm.family == Family.Rsa || algorithm.family == Family.Pss

  private def attempt[A](body: => A): Either[JwtError, A] =
    try Right(body)
    catch
      case error: GeneralSecurityException => Left(JwtError.InvalidKey(Option(error.getMessage).getOrElse(error.getClass.getName)))
      case error: IllegalArgumentException => Left(JwtError.InvalidKey(Option(error.getMessage).getOrElse(error.getClass.getName)))
