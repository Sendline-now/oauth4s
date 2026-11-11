package oauth4s.testkit

import java.time.Instant

import scala.util.Try

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.{OAuthClient, TokenResponse}
import oauth4s.json.{Json, JsonCodec, JsonDecoder}

abstract class JsonCodecSuite(library: String)(using codec: JsonCodec, decoder: JsonDecoder[JsonCodecSuite.User]) extends AnyWordSpec, Matchers:
  import JsonCodecSuite.*

  protected def roundTrip(token: TokenResponse): Either[String, TokenResponse]

  s"the $library codec" should {
    "parse a document into the same tree as the built-in one, and render it back" in {
      val expected = JsonCodec.native.parse(Document)

      (codec.parse(Document), codec.parse(Document).map(codec.render).flatMap(JsonCodec.native.parse)) shouldBe (expected, expected)
    }

    "refuse what is not JSON" in {
      List("""{"a":1,}""", "[1,2", "", """{"a":"b"} trailing""").map(codec.parse(_).isLeft) shouldBe List(true, true, true, true)
    }

    "be the parser an OAuth client reads its token response with" in {
      val http  = StubHttpClient.routes[Try](RuntimeSuite.Token.value -> RuntimeSuite.TokenReply)
      val token = OAuthClient[Try](RuntimeSuite.Config)(using summon, http, codec).clientCredentials().get

      (token.accessToken.value, token.scope.map(_.render), token.raw.string("refresh_token")) shouldBe ("access-1", Some("read write"), Some("refresh-1"))
    }
  }

  s"a $library decoder" should {
    "decode a type of the application from the tree, and report what does not fit" in {
      (Json.obj("id" -> Json.num(7), "name" -> Json.str("Ann")).as[User], Json.obj("id" -> Json.str("seven")).as[User].isLeft) shouldBe (Right(User(7, "Ann")), true)
    }

    "encode and decode a token response with the library's own codecs" in {
      val token = TokenResponse.fromJson(JsonCodec.native.parse(RuntimeSuite.TokenReply.body).toOption.get, Instant.parse("2026-01-01T00:00:00Z")).toOption.get

      roundTrip(token).map(_.copy(raw = Json.Null)) shouldBe Right(token.copy(raw = Json.Null))
    }
  }

object JsonCodecSuite:
  final case class User(id: Long, name: String)

  val Document: String = """{"a":1,"b":[true,null,"text",-2.5],"c":{"d":{}},"e":false,"f":"é\n\"","g":[]}"""
