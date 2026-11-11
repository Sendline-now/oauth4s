package oauth4s.testkit

import oauth4s.TokenResponse
import oauth4s.testkit.JsonCodecSuite.User
import oauth4s.json.{JsonCodec, JsonDecoder, JsonError}

import oauth4s.testkit.NativeCodecSpec.given

final class NativeCodecSpec extends JsonCodecSuite("built-in"):
  protected def roundTrip(token: TokenResponse): Either[String, TokenResponse] =
    JsonCodec.native.parse(token.toJson.render).left.map(_.message).flatMap(TokenResponse.fromJson(_).left.map(_.message))

object NativeCodecSpec:
  given userDecoder: JsonDecoder[User] = JsonDecoder.instance { json =>
    for
      id   <- json.long("id").toRight(JsonError.InvalidField("id", "is not a whole number"))
      name <- json.string("name").toRight(JsonError.MissingField("name"))
    yield User(id, name)
  }
