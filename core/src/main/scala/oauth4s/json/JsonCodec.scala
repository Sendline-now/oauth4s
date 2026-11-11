package oauth4s.json

import oauth4s.internal.NativeJsonCodec

trait JsonCodec:
  def parse(text: String): Either[JsonError, Json]
  def render(json: Json): String

object JsonCodec:
  inline def apply(using codec: JsonCodec): JsonCodec = codec

  given native: JsonCodec = NativeJsonCodec
