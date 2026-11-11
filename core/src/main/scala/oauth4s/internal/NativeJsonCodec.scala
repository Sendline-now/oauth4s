package oauth4s.internal

import scala.util.control.NoStackTrace

import oauth4s.json.{Json, JsonCodec, JsonError}

private[oauth4s] object NativeJsonCodec extends JsonCodec:
  private val MaxDepth  = 128
  private val MaxNumber = 128
  private val Number    = "-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]{1,4})?".r

  def parse(text: String): Either[JsonError, Json] =
    try Right(Reader(text).document())
    catch case failure: Failure => Left(failure.error)

  def render(json: Json): String =
    val out = StringBuilder()
    write(json, out)
    out.result()

  private final class Failure(val error: JsonError) extends RuntimeException(error.message), NoStackTrace

  private final class Reader(input: String):
    private var index = 0

    def document(): Json =
      val parsed = value(0)
      skipWhitespace()
      if index < input.length then fail("unexpected content after the document")
      parsed

    private def value(depth: Int): Json =
      if depth > MaxDepth then fail(s"the document nests deeper than $MaxDepth levels")
      skipWhitespace()
      peek match
        case '{'                                  => obj(depth)
        case '['                                  => arr(depth)
        case '"'                                  => Json.Str(string())
        case 't'                                  => literal("true", Json.Bool(true))
        case 'f'                                  => literal("false", Json.Bool(false))
        case 'n'                                  => literal("null", Json.Null)
        case char if char == '-' || isDigit(char) => number()
        case char                                 => fail(s"unexpected character '$char'")

    private def obj(depth: Int): Json =
      val fields = Vector.newBuilder[(String, Json)]
      index += 1
      skipWhitespace()

      if peek == '}'
      then index += 1
      else
        var open = true
        while open do
          skipWhitespace()
          if peek != '"' then fail("expected a member name")
          val name = string()
          skipWhitespace()
          expect(':')
          fields += name -> value(depth + 1)
          open = more('}')

      Json.Obj(fields.result())
    end obj

    private def arr(depth: Int): Json =
      val values = Vector.newBuilder[Json]
      index += 1
      skipWhitespace()

      if peek == ']'
      then index += 1
      else
        var open = true
        while open do
          values += value(depth + 1)
          open = more(']')

      Json.Arr(values.result())
    end arr

    private def more(close: Char): Boolean =
      skipWhitespace()
      val char = peek
      index += 1

      if char == ',' then true
      else if char == close then false
      else fail(s"expected ',' or '$close' but found '$char'")

    private def string(): String =
      index += 1
      val start = index
      while index < input.length && isPlain(input.charAt(index)) do index += 1

      if peek == '"'
      then
        index += 1
        input.substring(start, index - 1)
      else escaped(start)

    private def escaped(start: Int): String =
      val out = StringBuilder(input.substring(start, index))

      while peek != '"' do
        val char = input.charAt(index)
        index += 1

        if char == '\\' then out += escape()
        else if char < ' ' then fail("a control character must be escaped")
        else out += char

      index += 1
      out.result()
    end escaped

    private def escape(): Char =
      val char = peek
      index += 1

      char match
        case '"' | '\\' | '/' => char
        case 'b'              => '\b'
        case 'f'              => '\f'
        case 'n'              => '\n'
        case 'r'              => '\r'
        case 't'              => '\t'
        case 'u'              => unicode()
        case other            => fail(s"unknown escape '\\$other'")

    private def unicode(): Char =
      if index + 4 > input.length then fail("a unicode escape needs four hexadecimal digits")
      var code = 0
      var i    = 0

      while i < 4 do
        val digit = Character.digit(input.charAt(index + i), 16)
        if digit < 0 then fail("a unicode escape needs four hexadecimal digits")
        code = (code << 4) | digit
        i += 1

      index += 4
      code.toChar
    end unicode

    private def number(): Json =
      val start = index
      while index < input.length && isNumeric(input.charAt(index)) do index += 1
      val raw   = input.substring(start, index)

      if raw.length <= MaxNumber && Number.matches(raw)
      then Json.Num(BigDecimal(raw))
      else
        index = start
        fail(s"'${raw.take(MaxNumber)}' is not a number")

    private def literal(word: String, json: Json): Json =
      if !input.startsWith(word, index) then fail(s"expected '$word'")
      index += word.length
      json

    private def expect(expected: Char): Unit =
      if peek != expected then fail(s"expected '$expected'")
      index += 1

    private def peek: Char =
      if index < input.length
      then input.charAt(index)
      else fail("unexpected end of input")

    private def skipWhitespace(): Unit =
      while index < input.length && isWhitespace(input.charAt(index)) do index += 1

    private def isWhitespace(char: Char): Boolean = char == ' ' || char == '\t' || char == '\n' || char == '\r'
    private def isPlain(char: Char): Boolean      = char != '"' && char != '\\' && char >= ' '
    private def isDigit(char: Char): Boolean      = char >= '0' && char <= '9'
    private def isNumeric(char: Char): Boolean    = isDigit(char) || char == '-' || char == '+' || char == '.' || char == 'e' || char == 'E'

    private def fail(reason: String): Nothing = throw Failure(JsonError.Malformed(reason, index))
  end Reader

  private def write(json: Json, out: StringBuilder): Unit =
    json match
      case Json.Null        => out ++= "null"
      case Json.Bool(value) => out ++= value.toString
      case Json.Num(value)  => out ++= value.bigDecimal.toString
      case Json.Str(value)  => quote(value, out)
      case Json.Arr(values) =>
        out += '['
        values.zipWithIndex.foreach { (value, index) =>
          if index > 0 then out += ','
          write(value, out)
        }
        out += ']'
      case Json.Obj(fields) =>
        out += '{'
        fields.zipWithIndex.foreach { case ((name, value), index) =>
          if index > 0 then out += ','
          quote(name, out)
          out += ':'
          write(value, out)
        }
        out += '}'

  private def quote(value: String, out: StringBuilder): Unit =
    out += '"'
    value.foreach {
      case '"'                => out ++= "\\\""
      case '\\'               => out ++= "\\\\"
      case '\n'               => out ++= "\\n"
      case '\r'               => out ++= "\\r"
      case '\t'               => out ++= "\\t"
      case '\b'               => out ++= "\\b"
      case '\f'               => out ++= "\\f"
      case char if char < ' ' => out ++= f"\\u${char.toInt}%04x"
      case char               => out += char
    }
    out += '"'
