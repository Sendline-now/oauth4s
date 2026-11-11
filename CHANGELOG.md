# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - Unreleased

The first release.

### Added

- `oauth4s-core`: the OAuth 2.0 / 2.1 client, with no dependencies.
  - `OAuthClient[F]`: the authorization code flow with PKCE, `state`, the issuer check of RFC 9207 and an optional
    `nonce`; the client credentials and refresh grants; the device authorization grant of RFC 8628 with polling; token
    revocation of RFC 7009; introspection of RFC 7662; `requestToken` for any other grant; `resource` for a protected
    JSON resource.
  - Typed protocol values (`ClientId`, `ClientSecret`, `RedirectUri`, `Endpoint`, `Issuer`, `AccessToken`,
    `RefreshToken`, `IdToken`, `AuthorizationCode`, `State`, `Nonce`, `CodeVerifier`, `CodeChallenge`, `Scope`), with an
    `Endpoint` that is `https` or loopback by construction.
  - `ClientAuthentication`: `Public`, `SecretBasic`, `SecretPost` and `SecretMinted`.
  - `OAuthClientConfig`, `Endpoints` and `AuthorizationOptions` as builders, including the scope separator, forwarded
    callback parameters and a token response adapter for providers that deviate from the RFC.
  - Discovery per RFC 8414 with the OpenID configuration as a fallback, and the issuer of the document checked.
  - `TokenSource`: a cached token for client credentials or for a refresh token.
  - `OAuthError` with `ErrorCode`, and `OAuthException` that carries it; error documents are read whatever their HTTP
    status and in Facebook's and Instagram's shapes.
  - The three SPIs: `Effect[F]` (with an instance for `scala.util.Try`), `HttpClient[F]` and `JsonCodec` (with a
    built-in, dependency-free default), plus `JsonDecoder[A]` for the application's own types.
- `oauth4s-jwt`: JWT parsing, verification (RS, PS, ES and HS families, an algorithm allow-list, `exp`, `nbf`, `iss`,
  `aud`, custom checks) and signing; JWK and JWKS; `JwksSource` with a cached remote source that refetches on an
  unknown key id; PKCS#8 PEM private keys.
- `oauth4s-oidc`: `OidcClient[F]` — discovery, `openid` and nonce on authorization, ID token validation including
  `azp` and `at_hash`, UserInfo, RP-initiated logout.
- `oauth4s-providers`: Google, Microsoft, Apple, Facebook, Instagram, Strava, VK ID and X behind one `Provider[F]`
  interface, with a normalized `UserProfile`; `Provider.oauth` and `Provider.oidc` for a provider of your own.
- `oauth4s-cats`, `oauth4s-zio`, `oauth4s-kyo`, `oauth4s-rapid`: `Effect[F]` for each runtime.
- `oauth4s-http-jdk`, `oauth4s-http4s`, `oauth4s-sttp`: `HttpClient[F]` on each client.
- `oauth4s-circe`, `oauth4s-zio-json`, `oauth4s-jsoniter`: `JsonCodec`, a bridge from the library's decoders to
  `JsonDecoder`, and the library's codecs for `Json` and `TokenResponse`.
- `oauth4s-testkit`: `StubHttpClient`, `TestIssuer` and `FakeAuthorizationServer`, a loopback authorization server with
  discovery, PKCE verification, refresh token rotation, the device flow and signed ID tokens.
- A conformance suite per SPI that every runtime, JSON codec and HTTP client is tested against; an `it` module that
  runs an OpenID Connect login over real HTTP on the next Scala release, compiles every Scala block of the README and
  the recipes, and runs the recipes; examples for cats-effect, ZIO, `Try` and the providers; JMH benchmarks of parsing
  and ID token verification.

[0.1.0]: https://github.com/sendline-now/oauth4s/releases/tag/v0.1.0
