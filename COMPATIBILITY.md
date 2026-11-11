# Compatibility

[← back to the README](README.md)

* [What the artifacts promise](#what-the-artifacts-promise)
* [What it is built and tested against](#what-it-is-built-and-tested-against)
* [What it implements](#what-it-implements)
* [What the providers were checked against](#what-the-providers-were-checked-against)

## What the artifacts promise

* **Before `1.0.0`, a minor release may break the API.** Every break is listed in [CHANGELOG.md](CHANGELOG.md) with
  what to change. `versionScheme := "semver-spec"` says so to sbt and to Scala Steward.
* **From `1.0.0` on, MiMa checks** binary compatibility against the previous release on every build. Every deliberate
  break inside a major version is then an exclusion in `build.sbt` and an entry here, so nothing is waived silently.
* New members of `Effect` carry default implementations wherever one exists, as `realTime` and `blocking` do, so an
  instance of your own keeps compiling across minor releases. `HttpClient` and `JsonCodec` are one and two methods and
  are not expected to grow.
* Configuration types (`OAuthClientConfig`, `Endpoints`, `AuthorizationOptions`, `OidcConfig`, `JwtValidation`) are
  builders with a private constructor, not case classes, so adding an option is not a binary break.
* `OAuthClient`, `OidcClient`, `Provider`, `TokenSource` and `JwksSource` are traits the library implements. A method
  added to one of them is a source break for an implementation of your own — a decorator, a test double — and is listed
  in the changelog as one.
* `ErrorCode`, `OAuthError` and `JwtError` are enums that grow. Match with a default case.
* **A provider's constants follow the provider.** When Meta retires a Graph API version or a provider moves a host, the
  constant changes in a patch release. `config(…)` and `from(config)` are how to pin what a release would otherwise
  move.
* Everything under an `internal` package is not API, whatever its visibility in the bytecode.

## What it is built and tested against

| | Version |
| --- | --- |
| Scala | `3.9.0` (built on the LTS); the `it` module compiles on `3.10.0` against it |
| JDK, to run | 17 or newer: every module is compiled to Java 17 class files. `oauth4s-rapid` needs 21, because rapid runs on virtual threads, and `oauth4s-kyo` needs 25, because Kyo ships Java 25 class files |
| JDK, to build | 25 or newer, checked when the build loads |
| cats-effect | `3.7.1` |
| ZIO | `2.1.27` |
| Kyo | `1.0.0-RC7` |
| rapid | `2.9.9` |
| http4s | `0.23.38` (`http4s-client` only) |
| sttp client | `4.1.1` |
| circe | `0.14.17` |
| zio-json | `1.1.1` |
| jsoniter-scala | `2.42.3` (`jsoniter-scala-core` only; the macros are yours to add) |

`oauth4s-core`, `oauth4s-jwt`, `oauth4s-oidc`, `oauth4s-providers`, `oauth4s-http-jdk` and `oauth4s-testkit` have no
dependency outside the JDK and the Scala library. Cryptography is `java.security` and `javax.crypto`; no provider is
registered and none is required beyond the JDK's own.

## What it implements

| Specification | What is implemented | What is not |
| --- | --- | --- |
| [RFC 6749](https://www.rfc-editor.org/rfc/rfc6749), OAuth 2.0 | the authorization code, client credentials and refresh grants; `client_secret_basic`, `client_secret_post` and public clients; error responses | the implicit and password grants, on purpose |
| [OAuth 2.1](https://datatracker.ietf.org/doc/draft-ietf-oauth-v2-1/) (draft) | PKCE by default, exact redirect URI, no implicit, no password; what the client does when nothing is switched off | sender-constrained refresh tokens are the server's business |
| [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636), PKCE | `S256` and `plain`, verifier generation and validation | |
| [RFC 6750](https://www.rfc-editor.org/rfc/rfc6750), bearer tokens | the `Authorization: Bearer` header on `resource` and UserInfo | the form and query parameter forms |
| [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009), revocation | both token types, with the hint | |
| [RFC 7662](https://www.rfc-editor.org/rfc/rfc7662), introspection | the JSON response | the signed JWT response of RFC 9701 |
| [RFC 8414](https://www.rfc-editor.org/rfc/rfc8414), server metadata | discovery with the issuer check; the endpoints and the capability lists | signed metadata |
| [RFC 8628](https://www.rfc-editor.org/rfc/rfc8628), device grant | the request, polling, `slow_down`, expiry | |
| [RFC 9207](https://www.rfc-editor.org/rfc/rfc9207), issuer identification | `iss` on the callback compared with the configured issuer | |
| [RFC 8693](https://www.rfc-editor.org/rfc/rfc8693), token exchange | through `requestToken`; the grant type is named | a typed request |
| [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html) | the code flow, ID token validation (§3.1.3.7) including `azp`, `nonce` and `at_hash`, UserInfo | the implicit and hybrid flows, `claims` and `request` parameters, encrypted or signed UserInfo, `max_age` and `acr` checks |
| [OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html) | the provider configuration document | WebFinger |
| [RP-Initiated Logout 1.0](https://openid.net/specs/openid-connect-rpinitiated-1_0.html) | the logout URL | front- and back-channel logout |
| [RFC 7515](https://www.rfc-editor.org/rfc/rfc7515), JWS | compact serialization, verification and signing | JSON serialization, unencoded payloads (`b64`), `crit` extensions |
| [RFC 7517](https://www.rfc-editor.org/rfc/rfc7517), JWK | `RSA`, `EC` and `oct` public keys, key sets, `kid`, `use`, `alg` | private keys in JWK form, `x5c` chains, `OKP` |
| [RFC 7518](https://www.rfc-editor.org/rfc/rfc7518), JWA | `RS256`–`512`, `PS256`–`512`, `ES256`–`512`, `HS256`–`512` | `none`, on purpose; `EdDSA` |
| [RFC 7519](https://www.rfc-editor.org/rfc/rfc7519), JWT | `iss`, `sub`, `aud`, `exp`, `nbf`, `iat`, `jti` and custom checks | encrypted tokens (JWE) |
| OAuth 1.0a, OpenID 2.0 | nothing | everything: both are obsolete |

## What the providers were checked against

The endpoints, the scopes and the response shapes of the eight providers were read from each provider's documentation
and, where one exists, from its live discovery document on 2026-11-12:

| Provider | Hosts | Checked against |
| --- | --- | --- |
| Google | `accounts.google.com`, `oauth2.googleapis.com`, `openidconnect.googleapis.com` | the discovery document and the OpenID Connect guide |
| Microsoft | `login.microsoftonline.com`, `graph.microsoft.com` | the discovery documents of `common`, `organizations` and `consumers`, and the identity platform reference |
| Apple | `appleid.apple.com` | the discovery document and the Sign in with Apple REST API reference |
| Facebook | `www.facebook.com`, `graph.facebook.com`, Graph API `v27.0` | the manual login flow guide and the Graph API changelog |
| Instagram | `www.instagram.com`, `api.instagram.com`, `graph.instagram.com` | the Instagram API with Instagram Login guide |
| Strava | `www.strava.com` | the authentication guide and the API reference |
| VK ID | `id.vk.ru` | the VK ID API description |
| X | `x.com`, `api.x.com` | the RFC 8414 metadata and the OAuth 2.0 guide |

The tests run each provider against responses in the documented shape, not against the provider: none of them offers
a sandbox a build can sign in to. Three things could not be confirmed from documentation and are worth knowing:

- **X does not document the JSON of its token response.** It is read as an RFC 6749 response, which is what the
  metadata implies.
- **Apple does not document PKCE.** It is off for Apple; the client secret and the nonce are what protect that flow.
- **Facebook documents its token request as a `GET`.** See [known gaps](README.md#known-gaps).
