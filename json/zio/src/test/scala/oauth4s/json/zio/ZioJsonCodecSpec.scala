package oauth4s.json.zio

import zio.json.{DeriveJsonDecoder, JsonDecoder as ZioDecoder, JsonEncoder as ZioEncoder}

import oauth4s.TokenResponse
import oauth4s.testkit.JsonCodecSuite
import oauth4s.testkit.JsonCodecSuite.User

import oauth4s.json.zio.ZioJsonCodecSpec.given
import oauth4s.json.zio.ZioJsonInstances.given

final class ZioJsonCodecSpec extends JsonCodecSuite("zio-json"):
  protected def roundTrip(token: TokenResponse): Either[String, TokenResponse] =
    ZioDecoder[TokenResponse].decodeJson(ZioEncoder[TokenResponse].encodeJson(token, None))

object ZioJsonCodecSpec:
  given userDecoder: ZioDecoder[User] = DeriveJsonDecoder.gen[User]
