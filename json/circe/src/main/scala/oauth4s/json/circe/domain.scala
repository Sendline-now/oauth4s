package oauth4s.json.circe

import io.circe.Json as CirceJson

import oauth4s.json.Json

def toCirce(json: Json): CirceJson =
  json match
    case Json.Null        => CirceJson.Null
    case Json.Bool(value) => CirceJson.fromBoolean(value)
    case Json.Num(value)  => CirceJson.fromBigDecimal(value)
    case Json.Str(value)  => CirceJson.fromString(value)
    case Json.Arr(values) => CirceJson.fromValues(values.map(toCirce))
    case Json.Obj(fields) => CirceJson.fromFields(fields.map((name, value) => name -> toCirce(value)))

def fromCirce(json: CirceJson): Json =
  json.fold(
    Json.Null,
    value => Json.Bool(value),
    number => number.toBigDecimal.fold(Json.Null)(Json.Num(_)),
    value => Json.Str(value),
    values => Json.Arr(values.map(fromCirce)),
    fields => Json.Obj(fields.toVector.map((name, value) => name -> fromCirce(value))),
  )
