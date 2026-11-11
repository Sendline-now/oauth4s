package oauth4s

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

final class ValuesSpec extends AnyWordSpec, Matchers:

  "a scope" should {
    "drop blanks and duplicates, keep the order, and render with the separator a provider wants" in {
      val scope = Scope.of("read", " write ", "", "read")

      (scope.values, scope.render, scope.render(","), Scope.empty.isEmpty, (scope ++ Scope.of("admin", "write")).values) shouldBe (
        Vector("read", "write"),
        "read write",
        "read,write",
        true,
        Vector("read", "write", "admin"),
      )
    }

    "be read from what servers actually send: spaces, commas, or both" in {
      (Scope.parse("read write").values, Scope.parse("read,activity:read_all").values, Scope.parse(" a,  b ").values, Scope.parse("").isEmpty) shouldBe (
        Vector("read", "write"),
        Vector("read", "activity:read_all"),
        Vector("a", "b"),
        true,
      )
    }
  }

  "PKCE" should {
    "derive the challenge of RFC 7636 appendix B from its verifier" in {
      val verifier = CodeVerifier.parse("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

      (verifier.map(_.challenge(PkceMethod.S256).value), verifier.map(_.challenge(PkceMethod.Plain).value)) shouldBe (
        Right("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"),
        Right("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
      )
    }

    "refuse a verifier that is too short, too long or outside the unreserved characters" in {
      (CodeVerifier.parse("short"), CodeVerifier.parse("a" * 129), CodeVerifier.parse("a" * 42 + "+")) shouldBe (
        Left(OAuthError.InvalidCodeVerifier("it must be between 43 and 128 characters long")),
        Left(OAuthError.InvalidCodeVerifier("it must be between 43 and 128 characters long")),
        Left(OAuthError.InvalidCodeVerifier("it may only contain unreserved URI characters")),
      )
    }
  }

  "an endpoint" should {
    "be https, or http on a loopback address only, absolute and without a fragment" in {
      (
        Endpoint.parse("https://auth.example.com/token").map(_.value),
        Endpoint.parse("http://127.0.0.1:8080/token").map(_.value),
        Endpoint.parse("http://localhost/token").isRight,
        Endpoint.parse("http://auth.example.com/token"),
        Endpoint.parse("/token"),
        Endpoint.parse("https://auth.example.com/token#frag"),
        Endpoint.parse("https://auth example.com"),
      ) shouldBe (
        Right("https://auth.example.com/token"),
        Right("http://127.0.0.1:8080/token"),
        true,
        Left(OAuthError.InvalidUrl("http://auth.example.com/token", "it must use https, or http on a loopback address")),
        Left(OAuthError.InvalidUrl("/token", "it must be an absolute URL")),
        Left(OAuthError.InvalidUrl("https://auth.example.com/token#frag", "it must not have a fragment")),
        Left(OAuthError.InvalidUrl("https://auth example.com", "it is not a valid URL")),
      )
    }

    "throw on a literal that could never be used, where the mistake is the programmer's" in {
      (
        intercept[IllegalArgumentException](Endpoint("http://auth.example.com/token")).getMessage,
        intercept[IllegalArgumentException](ClientId("")).getMessage,
      ) shouldBe (
        "'http://auth.example.com/token' cannot be used: it must use https, or http on a loopback address",
        "requirement failed: a client id must not be empty",
      )
    }
  }

  "a callback" should {
    "be read from a redirected URL, ignoring the fragment, or from a form_post body" in {
      val fromUrl  = AuthorizationCallback.fromUrl("https://app.example.com/cb?code=a%20b&state=s1&iss=https%3A%2F%2Fauth.example.com#_")
      val fromForm = AuthorizationCallback.fromForm("code=c1&state=s2&user=%7B%22email%22%3A%22a%40b.c%22%7D")

      (fromUrl.code.map(_.value), fromUrl.state.map(_.value), fromUrl.issuer, fromForm.code.map(_.value), fromForm.parameter("user")) shouldBe (
        Some("a b"),
        Some("s1"),
        Some("https://auth.example.com"),
        Some("c1"),
        Some("""{"email":"a@b.c"}"""),
      )
    }
  }

  "an error code" should {
    "be one of the registered ones, or kept as the server spelled it" in {
      (ErrorCode.parse("invalid_grant"), ErrorCode.parse("user_cancelled_authorize"), ErrorCode.parse("slow_down").value) shouldBe (
        ErrorCode.InvalidGrant,
        ErrorCode.Other("user_cancelled_authorize"),
        "slow_down",
      )
    }
  }
