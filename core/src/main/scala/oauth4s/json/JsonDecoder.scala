package oauth4s.json

import scala.annotation.implicitNotFound

@implicitNotFound(
  "No JsonDecoder[${A}]. Import the instances of the JSON library whose decoder for ${A} you have: `oauth4s.json.circe.CirceInstances.given`, " +
    "`oauth4s.json.zio.ZioJsonInstances.given` or `oauth4s.json.jsoniter.JsoniterInstances.given`, or write one from the tree: `JsonDecoder.instance(json => ...)`."
)
trait JsonDecoder[A]:
  def decode(json: Json): Either[JsonError, A]

  final def map[B](f: A => B): JsonDecoder[B] =
    (json: Json) => decode(json).map(f)

  final def emap[B](f: A => Either[JsonError, B]): JsonDecoder[B] =
    (json: Json) => decode(json).flatMap(f)

object JsonDecoder:
  inline def apply[A](using instance: JsonDecoder[A]): JsonDecoder[A] = instance

  def instance[A](f: Json => Either[JsonError, A]): JsonDecoder[A] = (json: Json) => f(json)

  given raw: JsonDecoder[Json] = instance(json => Right(json))
