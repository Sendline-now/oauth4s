# Recipes

[← back to the README](README.md)

Answers to what wiring a service asks for once the [quick start](README.md#quick-start) runs. Every Scala block on this
page is copied from an example the integration tests compile, and `RecipesItSpec` runs the ones that do something
against an authorization server on a loopback port, so the page keeps up with the library.

* [Keeping the flow between two requests](#keeping-the-flow-between-two-requests)
* [Calling an API with a token that stays fresh](#calling-an-api-with-a-token-that-stays-fresh)
* [Keeping a user's tokens](#keeping-a-users-tokens)
* [Protecting your own API](#protecting-your-own-api)
* [A provider of your own](#a-provider-of-your-own)
* [Sign in with Apple, from the key file](#sign-in-with-apple-from-the-key-file)

## Keeping the flow between two requests

`authorize` and `login` happen in two different HTTP requests, and the flow has to get from one to the other. The
`state` is already a random, single-use name for it, so the simplest store is a map keyed by state:

```scala
final class SignIn(provider: Provider[IO], redirect: RedirectUri, pending: Ref[IO, Map[State, AuthorizationFlow]]):

  def begin: IO[String] =
    provider.authorize(redirect).flatMap(flow => pending.update(_ + (flow.state -> flow)).as(flow.url))

  def finish(callback: AuthorizationCallback): IO[Login] =
    val waiting = callback.state.fold(IO.none[AuthorizationFlow])(state => pending.modify(flows => (flows - state, flows.get(state))))

    waiting.flatMap {
      case Some(flow) => provider.login(flow, callback)
      case None       => IO.raiseError(IllegalStateException("no sign-in is waiting for this callback"))
    }

object SignIn:
  def of(provider: Provider[IO], redirect: RedirectUri): IO[SignIn] =
    Ref.of[IO, Map[State, AuthorizationFlow]](Map.empty).map(SignIn(provider, redirect, _))
```

`finish` removes the flow as it reads it, so a callback replayed by a back button or by an attacker finds nothing. A
callback whose state was never issued finds nothing either, which is the CSRF check done one step earlier.

In a service with more than one instance the map is a table or a cache with an expiry of a few minutes, or the flow
rides in an encrypted, `HttpOnly`, `SameSite=Lax` cookie. Its five fields are strings: `url`, `state.value`,
`redirectUri.value`, `verifier.map(_.value)` and `nonce.map(_.value)`. Whatever holds it must be readable only by the
browser that started the flow, because the verifier is what makes a stolen code useless.

## Calling an API with a token that stays fresh

`TokenSource` renews a token a minute before it expires. A token can also stop working early — revoked, or the
resource server restarted with new keys — and then the API says so with a `401`:

```scala
def withToken[A](tokens: TokenSource[IO])(call: AccessToken => IO[Either[Int, A]]): IO[A] =
  val once = tokens.accessToken.flatMap(call)

  once.flatMap {
    case Left(401) => tokens.invalidate >> once // revoked before it expired: one more try, with a new token
    case answer    => IO.pure(answer)
  }.flatMap(answer => IO.fromEither(answer.left.map(status => IllegalStateException(s"the API answered HTTP $status"))))
```

One retry, not a loop: a second `401` with a token issued a moment ago is not an expiry, it is a permission problem.

## Keeping a user's tokens

A user's access token outlives the request that obtained it. Store the token response as it is, and let a
`TokenSource` decide whether it still serves:

```scala
final class Sessions(client: OAuthClient[IO], load: String => IO[Option[String]], save: (String, String) => IO[Unit]):

  def signedIn(user: String, tokens: TokenResponse): IO[Unit] = save(user, tokens.asJson.noSpaces)

  def accessToken(user: String): IO[Option[AccessToken]] =
    load(user).flatMap {
      case None       => IO.none
      case Some(text) =>
        for
          stored <- IO.fromEither(decode[TokenResponse](text))
          fresh  <- TokenSource.refreshing(client, stored).token // the stored token while it is valid, a refreshed one after
          _      <- IO.whenA(fresh ne stored)(save(user, fresh.asJson.noSpaces))
        yield Some(fresh.accessToken)
    }
```

The stored document keeps `issued_at`, so `expiresAt` is still right after a restart. `fresh ne stored` is true exactly
when the server was asked, and that is the moment to save: with a provider that rotates refresh tokens — Strava, VK ID,
Microsoft — the old one is already dead.

A refresh token is a credential. Encrypt the column.

## Protecting your own API

The other side of OAuth: your service is the resource server, and it has to decide whether the bearer token on a
request is good. `oauth4s-jwt` is enough for that:

```scala
final class Guard(keys: JwksSource[IO], rules: JwtValidation):

  def allow(authorization: Option[String], scope: String): IO[Either[String, Jwt]] =
    authorization.collect { case s"Bearer $token" => token } match
      case None        => IO.pure(Left("the request carries no bearer token"))
      case Some(token) =>
        JwtVerifier.verify[IO](token, keys, rules).attempt.map {
          case Right(jwt) if granted(jwt).contains(scope) => Right(jwt)
          case Right(_)                                   => Left(s"the token was not granted $scope")
          case Left(refused: JwtException)                => Left(refused.error.message)
          case Left(other)                                => Left(s"the keys could not be read: ${other.getMessage}")
        }

  private def granted(jwt: Jwt): Scope = Scope.parse(jwt.claims.string("scope").getOrElse(""))

object Guard:
  def of(issuer: String, audience: String, jwks: Endpoint)(using HttpClient[IO]): Guard =
    Guard(JwksSource.remote[IO](jwks), JwtValidation.of(issuer, audience))
```

`JwtValidation.of` pins the issuer and the audience, and the default algorithm list is asymmetric only. What the
audience of an access token is, and whether the scope is a `scope` string or a `scp` array, differs between
authorization servers; check what yours issues before copying the last lines.

This works for access tokens that are JWTs. For opaque tokens the answer is `client.introspect(token)`, which asks the
authorization server on every call — cache the answer for a few seconds if the endpoint is not free.

## A provider of your own

Eight providers are in the box. Any other OAuth 2.0 server is a configuration and a function from its profile document
to a `UserProfile`:

```scala
object GitHub:
  val Authorization: Endpoint = Endpoint("https://github.com/login/oauth/authorize")
  val Token: Endpoint         = Endpoint("https://github.com/login/oauth/access_token")
  val User: Endpoint          = Endpoint("https://api.github.com/user")

  def apply(clientId: ClientId, clientSecret: ClientSecret)(using HttpClient[IO]): Provider[IO] =
    val config = OAuthClientConfig(clientId, ClientAuthentication.SecretPost(clientSecret), Endpoints.of(Authorization, Token))
      .withScope(Scope.of("read:user", "user:email"))
      .withUserAgent("my-service") // GitHub's API refuses a request without one

    Provider.oauth("github", OAuthClient[IO](config), User) { json =>
      json.long("id").toRight(OAuthError.MissingField("id")).map { id =>
        UserProfile("github", id.toString, json.string("name"), json.string("login"), json.string("email"), None, json.string("avatar_url"), json)
      }
    }
```

`Provider.oauth` keeps PKCE on. A server that rejects the PKCE parameters outright is rare; for one, start the flow
with `AuthorizationOptions.default.withoutPkce`.

Any OpenID Connect issuer — Keycloak, Auth0, Okta, Authentik, your own — needs no profile function at all, because the
UserInfo response is standard:

```scala
def keycloak(clientId: ClientId, clientSecret: ClientSecret)(using HttpClient[IO]): IO[Provider[IO]] =
  OidcClient
    .discover[IO](Issuer("https://sso.example.com/realms/main"), clientId, ClientAuthentication.SecretBasic(clientSecret))
    .map(Provider.oidc("keycloak", _)) // the profile is the UserInfo response
```

If the provider turns out to deviate in a way `OAuthClientConfig` cannot express, that is a
[missing knob](DESIGN.md#a-provider-is-a-configuration-and-its-exceptions), and an issue is welcome.

## Sign in with Apple, from the key file

Apple gives you a `.p8` file once. It is a PKCS#8 EC private key:

```scala
def apple(p8: Path)(using HttpClient[IO]): IO[Provider[IO]] =
  for
    pem <- IO.blocking(Files.readString(p8))
    key <- IO.fromEither(JwtSigner.privateKey(pem, "EC").left.map(_.toThrowable))
  yield Apple[IO](ClientId("com.example.web"), "TEAM123456", "KEY1234567", key) // Services ID, Team ID, Key ID
```

Three things about Apple that are not in the code:

- The callback is a `POST`, so the route that receives it reads a form: `provider.login(flow, AuthorizationCallback.fromForm(body))`.
  A cookie with `SameSite=Lax` is not sent on that request, so a flow kept in a cookie needs `SameSite=None; Secure`,
  or keep it on the server.
- The user's name arrives only on their first sign-in, in the `user` field of that form. `login` reads it into the
  profile; store it then, because Apple will not send it again.
- The email may be a relay address at `privaterelay.appleid.com`. It is stable for your application and accepts mail
  only from the domains you registered with Apple.
