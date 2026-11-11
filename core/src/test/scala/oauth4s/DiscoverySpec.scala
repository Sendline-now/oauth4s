package oauth4s

import scala.util.Try

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import oauth4s.http.HttpResponse
import oauth4s.OAuthClientSpec.errorOf

final class DiscoverySpec extends AnyWordSpec, Matchers:
  import DiscoverySpec.*

  private given ManualTime = ManualTime()

  "discovery" should {
    "ask RFC 8414's well-known path, inserted before the issuer's own path" in {
      val issuer = Issuer("https://auth.example.com/tenant-1")
      val http   = RecordingHttp.routes("https://auth.example.com/.well-known/oauth-authorization-server/tenant-1" -> HttpResponse.json(metadata(issuer)))

      Discovery.oauth[Try](issuer)(using summon, http).map(_.tokenEndpoint.map(_.value)).get shouldBe Some("https://auth.example.com/tenant-1/token")
    }

    "ask OpenID Connect's well-known path, appended to the issuer's own path" in {
      val issuer = Issuer("https://auth.example.com/tenant-1/")
      val http   = RecordingHttp.routes("https://auth.example.com/tenant-1/.well-known/openid-configuration" -> HttpResponse.json(metadata(issuer)))

      Discovery.openid[Try](issuer)(using summon, http).map(_.supportsPkce).get shouldBe true
    }

    "fall back from the OAuth document to the OpenID one, and report the first failure when both are missing" in {
      val issuer = Issuer("https://auth.example.com")
      val openid = RecordingHttp.routes("https://auth.example.com/.well-known/openid-configuration" -> HttpResponse.json(metadata(issuer)))
      val none   = RecordingHttp.routes()

      (
        Discovery.discover[Try](issuer)(using summon, openid).map(_.issuer).get,
        errorOf(Discovery.discover[Try](issuer)(using summon, none)),
        none.requests.map(_.url)
      ) shouldBe (
        issuer,
        OAuthError.Rejected(ErrorCode.Other("not_found"), None, None),
        Vector("https://auth.example.com/.well-known/oauth-authorization-server", "https://auth.example.com/.well-known/openid-configuration"),
      )
    }

    "refuse a document that answers for another issuer, or that points at an endpoint a secret must not go to" in {
      val issuer   = Issuer("https://auth.example.com")
      val other    = RecordingHttp.routes("https://auth.example.com/.well-known/openid-configuration" -> HttpResponse.json(metadata(Issuer("https://evil.example.com"))))
      val insecure = metadata(issuer).replace("https://auth.example.com/token", "http://auth.example.com/token")
      val plain    = RecordingHttp.routes("https://auth.example.com/.well-known/openid-configuration" -> HttpResponse.json(insecure))

      (errorOf(Discovery.openid[Try](issuer)(using summon, other)), errorOf(Discovery.openid[Try](issuer)(using summon, plain))) shouldBe (
        OAuthError.IssuerMismatch(issuer, "https://evil.example.com"),
        OAuthError.InvalidUrl("http://auth.example.com/token", "it must use https, or http on a loopback address"),
      )
    }

    "build a client from the discovered endpoints that remembers its issuer" in {
      val issuer = Issuer("https://auth.example.com")
      val http   = RecordingHttp.routes("https://auth.example.com/.well-known/oauth-authorization-server" -> HttpResponse.json(metadata(issuer)))
      val client = OAuthClient.discover[Try](issuer, Fixtures.ClientId, ClientAuthentication.Public)(using summon, http).get

      (client.config.issuer, client.config.endpoints.token.value, client.config.endpoints.jwks.map(_.value)) shouldBe (
        Some(issuer),
        "https://auth.example.com/token",
        Some("https://auth.example.com/jwks"),
      )
    }
  }

object DiscoverySpec:

  def metadata(issuer: Issuer): String =
    val base = issuer.value.stripSuffix("/")

    s"""{"issuer":"${issuer.value}","authorization_endpoint":"$base/authorize","token_endpoint":"$base/token","jwks_uri":"$base/jwks",""" +
      s""""userinfo_endpoint":"$base/userinfo","code_challenge_methods_supported":["S256"],"grant_types_supported":["authorization_code"]}"""
