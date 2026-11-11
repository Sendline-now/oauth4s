package oauth4s.json.circe

import io.circe.parser
import io.circe.ParsingFailure

import oauth4s.json.{Json, JsonCodec, JsonError}

trait CodecInstance:

  given circeCodec: JsonCodec = new JsonCodec:
    def parse(text: String): Either[JsonError, Json] =
      parser.parse(text) match
        case Right(parsed)                    => Right(fromCirce(parsed))
        case Left(ParsingFailure(message, _)) => Left(JsonError.Malformed(message, 0))

    def render(json: Json): String = toCirce(json).noSpaces
