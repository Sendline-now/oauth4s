package oauth4s.json.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString, JsonReaderException, JsonValueCodec}

import oauth4s.TokenResponse
import oauth4s.testkit.JsonCodecSuite
import oauth4s.testkit.JsonCodecSuite.User

import oauth4s.json.jsoniter.JsoniterCodecSpec.given
import oauth4s.json.jsoniter.JsoniterInstances.given

final class JsoniterCodecSpec extends JsonCodecSuite("jsoniter"):
  protected def roundTrip(token: TokenResponse): Either[String, TokenResponse] =
    try Right(readFromString[TokenResponse](writeToString(token)))
    catch case error: JsonReaderException => Left(error.getMessage)

object JsoniterCodecSpec:
  given userCodec: JsonValueCodec[User] = JsonCodecMaker.make
