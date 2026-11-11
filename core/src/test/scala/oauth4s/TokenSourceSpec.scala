package oauth4s

import scala.util.Try

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.http.HttpResponse
import oauth4s.OAuthClientSpec.{clientOf, errorOf}

import scala.concurrent.duration.given

final class TokenSourceSpec extends AnyWordSpec, Matchers:
  import TokenSourceSpec.*

  "a client credentials token source" should {
    "ask once, serve the token until it is about to expire, and ask again after that or when told the token was refused" in {
      val time          = ManualTime()
      val http          = RecordingHttp.sequence(token("access-1"), token("access-2"), token("access-3"))
      given Effect[Try] = time
      val source        = TokenSource.clientCredentials(clientOf(http, time = time), Scope.of("read"), leeway = 1.minute)

      val first    = List.fill(3)(source.accessToken.get.value)
      time.advance(58.minutes)
      val early    = source.accessToken.get.value
      time.advance(90.seconds)
      val renewed  = source.accessToken.get.value
      source.invalidate.get
      val replaced = source.accessToken.get.value

      (first, early, renewed, replaced, http.requests.size) shouldBe (List("access-1", "access-1", "access-1"), "access-1", "access-2", "access-3", 3)
    }
  }

  "a refreshing token source" should {
    "serve the initial token, refresh with its refresh token, and keep that token when the server does not rotate it" in {
      val time          = ManualTime()
      val http          = RecordingHttp.sequence(token("access-1", Some("refresh-1")), token("access-2"), token("access-3", Some("refresh-3")), token("access-4"))
      given Effect[Try] = time
      val client        = clientOf(http, time = time)
      val source        = TokenSource.refreshing(client, client.clientCredentials().get)

      val initial = source.accessToken.get.value
      time.advance(1.hour)
      val second  = (source.accessToken.get.value, http.lastForm.get("refresh_token"))
      time.advance(1.hour)
      val third   = (source.accessToken.get.value, http.lastForm.get("refresh_token"))
      time.advance(1.hour)
      val fourth  = (source.accessToken.get.value, http.lastForm.get("refresh_token"))

      (initial, second, third, fourth) shouldBe (
        "access-1",
        ("access-2", Some("refresh-1")),
        ("access-3", Some("refresh-1")),
        ("access-4", Some("refresh-3")),
      )
    }

    "fail once the token has expired when there was never a refresh token to renew it with" in {
      val time          = ManualTime()
      val http          = RecordingHttp.sequence(token("access-1"))
      given Effect[Try] = time
      val client        = clientOf(http, time = time)
      val source        = TokenSource.refreshing(client, client.clientCredentials().get)

      time.advance(2.hours)

      errorOf(source.accessToken) shouldBe OAuthError.MissingField("refresh_token")
    }
  }

object TokenSourceSpec:

  def token(access: String, refresh: Option[String] = None): HttpResponse =
    HttpResponse.json(s"""{"access_token":"$access","expires_in":3600${refresh.fold("")(value => s""","refresh_token":"$value"""")}}""")
