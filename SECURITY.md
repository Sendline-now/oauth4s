# Security policy

[← back to the README](README.md)

## Reporting a vulnerability

Do not open a public issue for a security problem. Send a private report to `security@sendline.now` with the affected
module, the version, and the smallest reproduction you have. We answer within five business days and credit the
reporter in the release notes unless asked not to.

## What this library assumes

`oauth4s` is a client: it builds requests, reads responses and verifies tokens. It trusts the configuration it is
given, and it does not defend against a compromised application. The assumptions it does make are enforced:

| Assumption | Where it is enforced |
| --- | --- |
| The authorization server is reached over TLS | `OAuthClientConfig.validate` rejects `http://` except on loopback addresses |
| A token endpoint belongs to the issuer that was asked | `Discovery` rejects a metadata document whose `issuer` differs from the requested one |
| The `state` of a callback is the one the flow started with | `complete` and `AuthorizationCallback.requireCode` return `InvalidState` |
| PKCE verifiers are unguessable | `CodeVerifier.generate` reads 32 bytes from `SecureRandom` |
| An id token was signed by the issuer's key | `JwtVerifier` verifies the signature against the JWKS, refuses `crit` headers and `alg` values outside the allowed set |
| An id token is current and meant for this client | `JwtVerifier` checks `exp`, `nbf`, `iss`, `aud` and `nonce` with a configurable clock skew |
| The client secret does not end up in a log | `OAuthClientConfig.toString` masks it; no other type prints a secret |

## What the library does not protect against

- **A malicious authorization server.** The client verifies signatures and claims, but a server that issues a token
  for the wrong subject is the server's problem, and OIDC discovery cannot detect it.
- **Logging by the application.** `ClientSecret.value`, `AccessToken.value` and every other `value` accessor returns
  the raw string. If your application logs a token, the token is in the log.
- **Token storage.** The library does not persist tokens. Where a refresh token lives, how it is encrypted, and who can
  read it are decisions of the application.
- **Rate limiting and abuse.** `TokenSource` refreshes when a token is about to expire; a loop that forces refreshes
  will reach the server as fast as it is called.

## Supported versions

The latest release is supported. Security fixes are released as patch versions of the current minor line.

## Dependency surface

`oauth4s-core` and `oauth4s-jwt` have no dependencies beyond the JDK. Every other module depends on the library it
wraps, at the versions in `project/Dependencies.scala`, and a vulnerability in one of them is reported here as well.
