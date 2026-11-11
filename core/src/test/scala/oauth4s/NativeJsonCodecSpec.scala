package oauth4s

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.json.{Json, JsonCodec, JsonDecoder, JsonError}

final class NativeJsonCodecSpec extends AnyWordSpec, Matchers:
  private val codec = JsonCodec.native

  "the built-in codec" should {
    "parse every JSON type, and render the tree back to the same text" in {
      val text = """{"a":1,"b":[true,null,"text"],"c":{"d":-2.5e3},"e":false,"f":"é\n\"\\"}"""

      (codec.parse(text), codec.parse(text).map(codec.render).flatMap(codec.parse)) shouldBe (
        Right(
          Json.obj(
            "a" -> Json.num(1),
            "b" -> Json.arr(Json.bool(true), Json.Null, Json.str("text")),
            "c" -> Json.obj("d" -> Json.Num(BigDecimal("-2.5e3"))),
            "e" -> Json.bool(false),
            "f" -> Json.str("é\n\"\\"),
          )
        ),
        codec.parse(text),
      )
    }

    "say where a document stops being JSON" in {
      (
        codec.parse("""{"a":1,}"""),
        codec.parse("""{"a" 1}"""),
        codec.parse("[1,2"),
        codec.parse("""{"a":01}"""),
        codec.parse("""{"a":"b"} trailing"""),
        codec.parse(""),
      ) shouldBe (
        Left(JsonError.Malformed("expected a member name", 7)),
        Left(JsonError.Malformed("expected ':'", 5)),
        Left(JsonError.Malformed("unexpected end of input", 4)),
        Left(JsonError.Malformed("'01' is not a number", 5)),
        Left(JsonError.Malformed("unexpected content after the document", 10)),
        Left(JsonError.Malformed("unexpected end of input", 0)),
      )
    }

    "refuse a document nested deeper than 128 levels instead of overflowing the stack" in {
      val deep = "[" * 5000 + "]" * 5000

      codec.parse(deep).left.map(_.message.take(41)) shouldBe Left("the document nests deeper than 128 levels")
    }

    "refuse a number whose exponent is too long to be anything but an attack" in {
      codec.parse("1e999999999").isLeft shouldBe true
    }

    "escape control characters and quotes when rendering" in {
      codec.render(Json.obj("k\"" -> Json.str("a\tb\u0001"))) shouldBe """{"k\"":"a\tb\u0001"}"""
    }
  }

  "a JSON tree" should {
    "give the last of two members with the same name, and typed access by name or path" in {
      val json = codec.parse("""{"id":1,"id":2,"user":{"name":"Ann","tags":["a","b",3]},"ok":true}""").toOption.get

      (json.long("id"), json.path("user", "name").flatMap(_.asString), json.path("user").map(_.strings("tags")), json.boolean("ok"), json.string("ok")) shouldBe (
        Some(2L),
        Some("Ann"),
        Some(Vector("a", "b")),
        Some(true),
        None,
      )
    }

    "decode through a JsonDecoder, the tree itself being the default one" in {
      val named = JsonDecoder.instance(json => json.string("name").toRight(JsonError.MissingField("name")))
      val json  = Json.obj("name" -> Json.str("Ann"))

      (json.as[Json], json.as(using named), Json.empty.as(using named), json.as(using named.map(_.length))) shouldBe (
        Right(json),
        Right("Ann"),
        Left(JsonError.MissingField("name")),
        Right(3),
      )
    }
  }
