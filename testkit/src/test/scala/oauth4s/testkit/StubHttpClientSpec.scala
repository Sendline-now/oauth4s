package oauth4s.testkit

import java.util.concurrent.atomic.AtomicReference

import scala.util.Try

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.Effect
import oauth4s.http.{HttpRequest, HttpResponse}

final class StubHttpClientSpec extends AnyWordSpec, Matchers:
  import Effect.blocking

  "a stub routed by a function" should {
    "answer from what is known when the request arrives, and answer 404 to what it does not route" in {
      val nonce = AtomicReference("first")
      val http  = StubHttpClient.routesBy[Try] {
        case request if request.url.startsWith("https://auth.example.com/token") => HttpResponse.json(s"""{"nonce":"${nonce.get}"}""")
      }

      val before = http.send(HttpRequest.post("https://auth.example.com/token", "grant_type=authorization_code")).map(_.body)
      nonce.set("second")
      val after  = http.send(HttpRequest.post("https://auth.example.com/token?x=1", "grant_type=authorization_code")).map(_.body)
      val other  = http.send(HttpRequest.get("https://auth.example.com/userinfo")).map(_.status)

      (before.get, after.get, other.get, http.requests.size) shouldBe ("""{"nonce":"first"}""", """{"nonce":"second"}""", 404, 3)
    }
  }
