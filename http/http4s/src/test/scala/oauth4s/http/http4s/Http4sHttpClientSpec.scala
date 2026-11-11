package oauth4s.http.http4s

import java.util.concurrent.atomic.AtomicReference

import org.http4s.client.Client
import org.typelevel.ci.CIString
import cats.effect.{IO, Resource}
import org.scalatest.wordspec.AnyWordSpec
import org.http4s.{Header, Response, Status}
import org.scalatest.matchers.should.Matchers

import oauth4s.http.{HttpRequest, HttpResponse}
import oauth4s.{ClientAuthentication, ClientId, ClientSecret, Endpoint, Endpoints, OAuthClient, OAuthClientConfig, OAuthError, OAuthException, Scope}

import cats.effect.unsafe.implicits.global
import oauth4s.runtime.cats.CatsInstances.given

final class Http4sHttpClientSpec extends AnyWordSpec, Matchers:
  import Http4sHttpClientSpec.*

  "an http4s client" should {
    "carry the method, the headers and the body of a request, and the status, headers and body of the answer" in {
      val seen     = AtomicReference(Option.empty[(String, String, Option[String], String)])
      val client   = Http4sHttpClient(recording(seen, Status.Created, """{"ok":true}"""))
      val response = client
        .send(HttpRequest.post("https://auth.example.com/token", "grant_type=client_credentials", "Content-Type" -> "application/x-www-form-urlencoded"))
        .unsafeRunSync()

      (seen.get, response) shouldBe (
        Some(("POST", "https://auth.example.com/token", Some("application/x-www-form-urlencoded"), "grant_type=client_credentials")),
        HttpResponse.of(201, """{"ok":true}""", "X-Request-Id" -> "r-1"),
      )
    }

    "serve an OAuth client, whose transport failures keep their cause" in {
      val seen   = AtomicReference(Option.empty[(String, String, Option[String], String)])
      val config =
        OAuthClientConfig(ClientId("client-1"), ClientAuthentication.SecretPost(ClientSecret("s3cret")), Endpoints.of(Endpoint("https://auth.example.com/token")))
      val token  = OAuthClient[IO](config)(using summon, Http4sHttpClient(recording(seen, Status.Ok, """{"access_token":"access-1"}""")))
        .clientCredentials(Scope.of("read"))
        .unsafeRunSync()
      val broken =
        OAuthClient[IO](config)(using summon, Http4sHttpClient(Client[IO](_ => Resource.eval(IO.raiseError(Boom))))).clientCredentials().attempt.unsafeRunSync()

      (token.accessToken.value, seen.get.map(_._4), broken.left.map { case error: OAuthException => error.error }) shouldBe (
        "access-1",
        Some("grant_type=client_credentials&scope=read&client_id=client-1&client_secret=s3cret"),
        Left(OAuthError.Transport(Boom)),
      )
    }
  }

object Http4sHttpClientSpec:
  object Boom extends RuntimeException("connection reset")

  def recording(seen: AtomicReference[Option[(String, String, Option[String], String)]], status: Status, body: String): Client[IO] =
    Client[IO] { request =>
      Resource.eval(
        request.bodyText.compile.string.map { sent =>
          seen.set(Some((request.method.name, request.uri.renderString, request.headers.get(CIString("Content-Type")).map(_.head.value), sent)))
          Response[IO](status).withEntity(body).withHeaders(Header.Raw(CIString("X-Request-Id"), "r-1"))
        }
      )
    }
