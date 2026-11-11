package oauth4s.json.circe

import io.circe.Decoder

import oauth4s.json.{JsonDecoder, JsonError}

trait DecoderInstance:

  given fromCirceDecoder: [A] => (decoder: Decoder[A]) => JsonDecoder[A] =
    json => decoder.decodeJson(toCirce(json)).left.map(failure => JsonError.Custom(failure.getMessage))
