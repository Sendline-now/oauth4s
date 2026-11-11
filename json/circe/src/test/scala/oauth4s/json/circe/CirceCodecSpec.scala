package oauth4s.json.circe

import io.circe.Decoder
import io.circe.syntax.*
import io.circe.parser.decode

import oauth4s.TokenResponse
import oauth4s.testkit.JsonCodecSuite
import oauth4s.testkit.JsonCodecSuite.User

import oauth4s.json.circe.CirceCodecSpec.given
import oauth4s.json.circe.CirceInstances.given

final class CirceCodecSpec extends JsonCodecSuite("circe"):
  protected def roundTrip(token: TokenResponse): Either[String, TokenResponse] = decode[TokenResponse](token.asJson.noSpaces).left.map(_.getMessage)

object CirceCodecSpec:
  given userDecoder: Decoder[User] = Decoder.forProduct2("id", "name")(User.apply)
