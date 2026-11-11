# Roadmap

[← back to the README](README.md)

What is planned, in the order it matters. Nothing here is a promise about a version.

## Next

- **More providers.** GitHub, GitLab, Discord, Yandex, LinkedIn, Twitch, Slack, Spotify, Amazon, Telegram and the rest
  of the well-known ones. A provider is an object with `config`, `apply`, `from` and `profile`, and a case in
  `ProvidersSpec`; the four knobs of `OAuthClientConfig` have covered every deviation so far.
- **Single-flight refresh.** A `TokenSource` that refreshes once per expiry, not once per caller, needs a
  `Deferred`-shaped primitive. That belongs in `runtime/cats`, `runtime/zio`, `runtime/kyo` and `runtime/rapid`, behind
  the same `TokenSource` interface.
- **`private_key_jwt` client authentication (RFC 7523).** `JwtSigner` and `ClientAuthentication.SecretMinted` are most
  of it; what is missing is the `client_assertion` form of the request.
- **A non-blocking JDK client.** `sendAsync` needs an asynchronous boundary that `Effect` does not have yet.

## Later

- **Pushed authorization requests (RFC 9126)** and **DPoP (RFC 9449)**, the two OAuth 2.1-era extensions providers are
  starting to ask for.
- **Dynamic client registration (RFC 7591).**
- **Signed UserInfo responses, EdDSA and JWE**, when a provider we support needs them.
- **Facebook's OpenID Connect flow**, once it leaves testing on Meta's side.
- **More JSON bridges** — play-json, upickle, borer — in the shape `json/circe` already has.
- **An http4s middleware** that adds the token of a `TokenSource` to outgoing requests.

## Never

- OAuth 1.0a and OpenID 2.0.
- The implicit and password grants.
- A client that keeps a session, and so decides where the flow state lives.
- An effect system, a JSON library, an HTTP client or a logging framework as a dependency of `core`.
- Retries, because whether a retry is safe depends on the grant in flight.
