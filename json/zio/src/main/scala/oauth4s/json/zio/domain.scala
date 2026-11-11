package oauth4s.json.zio

import zio.json.ast.Json as ZioJson

import oauth4s.json.Json

def toZio(json: Json): ZioJson =
  json match
    case Json.Null        => ZioJson.Null
    case Json.Bool(value) => ZioJson.Bool(value)
    case Json.Num(value)  => ZioJson.Num(value.bigDecimal)
    case Json.Str(value)  => ZioJson.Str(value)
    case Json.Arr(values) => ZioJson.Arr(values.map(toZio)*)
    case Json.Obj(fields) => ZioJson.Obj(fields.map((name, value) => name -> toZio(value))*)

def fromZio(json: ZioJson): Json =
  json match
    case ZioJson.Null        => Json.Null
    case ZioJson.Bool(value) => Json.Bool(value)
    case ZioJson.Num(value)  => Json.Num(BigDecimal(value))
    case ZioJson.Str(value)  => Json.Str(value)
    case ZioJson.Arr(values) => Json.Arr(values.map(fromZio).toVector)
    case ZioJson.Obj(fields) => Json.Obj(fields.map((name, value) => name -> fromZio(value)).toVector)
