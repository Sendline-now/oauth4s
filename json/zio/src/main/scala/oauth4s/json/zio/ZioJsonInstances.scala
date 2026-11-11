package oauth4s.json.zio

import zio.json.ast.Json as ZioJson
import zio.json.{JsonDecoder as ZioDecoder, JsonEncoder as ZioEncoder}

import oauth4s.TokenResponse
import oauth4s.internal.Secrets
import oauth4s.json.{Json, JsonCodec, JsonDecoder, JsonError}

trait ZioJsonInstances:

  given zioJsonCodec: JsonCodec = new JsonCodec:
    def parse(text: String): Either[JsonError, Json] = ZioJsonInstances.whole(text)

    def render(json: Json): String = ZioEncoder[ZioJson].encodeJson(toZio(json), None).toString

  given fromZioDecoder: [A] => (decoder: ZioDecoder[A]) => JsonDecoder[A] =
    json => decoder.fromJsonAST(toZio(json)).left.map(JsonError.Custom(_))

  given treeEncoder: ZioEncoder[Json] = ZioEncoder[ZioJson].contramap(toZio)

  given treeDecoder: ZioDecoder[Json] = ZioDecoder[ZioJson].map(fromZio)

  given tokenResponseEncoder: ZioEncoder[TokenResponse] = ZioEncoder[ZioJson].contramap(token => toZio(token.toJson))

  given tokenResponseDecoder: ZioDecoder[TokenResponse] =
    ZioDecoder[ZioJson].mapOrFail(tree => TokenResponse.fromJson(fromZio(tree)).left.map(_.message))

object ZioJsonInstances extends ZioJsonInstances:
  private val Sentinel = Secrets.token(16)

  private def whole(text: String): Either[JsonError, Json] =
    ZioDecoder[ZioJson].decodeJson(s"""[$text,"$Sentinel"]""") match
      case Right(ZioJson.Arr(elements)) if elements.size == 2 && elements(1) == ZioJson.Str(Sentinel) => Right(fromZio(elements(0)))
      case Right(_)                                                                                   => Left(JsonError.Malformed("unexpected content after the document", 0))
      case Left(message)                                                                              => Left(JsonError.Malformed(message, 0))
