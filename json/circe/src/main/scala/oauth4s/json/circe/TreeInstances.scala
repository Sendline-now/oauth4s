package oauth4s.json.circe

import io.circe.{Decoder, DecodingFailure, Encoder}

import oauth4s.json.Json
import oauth4s.TokenResponse

trait TreeInstances:

  given treeEncoder: Encoder[Json] = Encoder.instance(toCirce)

  given treeDecoder: Decoder[Json] = Decoder.instance(cursor => Right(fromCirce(cursor.value)))

  given tokenResponseEncoder: Encoder[TokenResponse] = Encoder.instance(token => toCirce(token.toJson))

  given tokenResponseDecoder: Decoder[TokenResponse] =
    Decoder.instance(cursor => TokenResponse.fromJson(fromCirce(cursor.value)).left.map(error => DecodingFailure(error.message, cursor.history)))
