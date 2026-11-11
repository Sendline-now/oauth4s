package oauth4s.json.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString, JsonReader, JsonReaderException, JsonValueCodec, JsonWriter}

import oauth4s.TokenResponse
import oauth4s.json.{Json, JsonCodec, JsonDecoder, JsonError}

trait JsoniterInstances:

  given treeCodec: JsonValueCodec[Json] = JsoniterInstances.TreeCodec

  given tokenResponseCodec: JsonValueCodec[TokenResponse] = JsoniterInstances.TokenResponseCodec

  given jsoniterCodec: JsonCodec = new JsonCodec:
    def parse(text: String): Either[JsonError, Json] =
      try Right(readFromString[Json](text))
      catch case error: JsonReaderException => Left(JsonError.Malformed(error.getMessage, 0))

    def render(json: Json): String = writeToString(json)

  given fromJsoniterCodec: [A] => (codec: JsonValueCodec[A]) => JsonDecoder[A] =
    json =>
      try Right(readFromString[A](writeToString(json)))
      catch case error: JsonReaderException => Left(JsonError.Custom(error.getMessage))

object JsoniterInstances extends JsoniterInstances:
  private val MaxDepth = 128

  private object TokenResponseCodec extends JsonValueCodec[TokenResponse]:
    def nullValue: TokenResponse = null

    def decodeValue(in: JsonReader, default: TokenResponse): TokenResponse =
      TokenResponse.fromJson(TreeCodec.decodeValue(in, Json.Null)).fold(error => in.decodeError(error.message), token => token)

    def encodeValue(token: TokenResponse, out: JsonWriter): Unit = TreeCodec.encodeValue(token.toJson, out)

  private object TreeCodec extends JsonValueCodec[Json]:
    def nullValue: Json = Json.Null

    def decodeValue(in: JsonReader, default: Json): Json = read(in, MaxDepth)

    def encodeValue(json: Json, out: JsonWriter): Unit =
      json match
        case Json.Null        => out.writeNull()
        case Json.Bool(value) => out.writeVal(value)
        case Json.Num(value)  => out.writeVal(value)
        case Json.Str(value)  => out.writeVal(value)
        case Json.Arr(values) =>
          out.writeArrayStart()
          values.foreach(encodeValue(_, out))
          out.writeArrayEnd()
        case Json.Obj(fields) =>
          out.writeObjectStart()
          fields.foreach { (name, value) =>
            out.writeKey(name)
            encodeValue(value, out)
          }
          out.writeObjectEnd()

    private def read(in: JsonReader, depth: Int): Json =
      if depth <= 0 then in.decodeError(s"the document nests deeper than $MaxDepth levels")

      in.nextToken() match
        case '"'                                                     =>
          in.rollbackToken()
          Json.Str(in.readString(null))
        case 't' | 'f'                                               =>
          in.rollbackToken()
          Json.Bool(in.readBoolean())
        case 'n'                                                     => in.readNullOrError(Json.Null, "expected a JSON value")
        case '['                                                     => array(in, depth)
        case '{'                                                     => obj(in, depth)
        case token if token == '-' || (token >= '0' && token <= '9') =>
          in.rollbackToken()
          Json.Num(in.readBigDecimal(null))
        case _                                                       => in.decodeError("expected a JSON value")
    end read

    private def array(in: JsonReader, depth: Int): Json =
      val values = Vector.newBuilder[Json]

      if !in.isNextToken(']') then
        in.rollbackToken()
        values += read(in, depth - 1)
        while in.isNextToken(',') do values += read(in, depth - 1)
        if !in.isCurrentToken(']') then in.arrayEndOrCommaError()

      Json.Arr(values.result())

    private def obj(in: JsonReader, depth: Int): Json =
      val fields = Vector.newBuilder[(String, Json)]

      if !in.isNextToken('}') then
        in.rollbackToken()
        fields += in.readKeyAsString()                              -> read(in, depth - 1)
        while in.isNextToken(',') do fields += in.readKeyAsString() -> read(in, depth - 1)
        if !in.isCurrentToken('}') then in.objectEndOrCommaError()

      Json.Obj(fields.result())
