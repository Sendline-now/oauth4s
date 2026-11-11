package oauth4s.it.docs.flow

import java.time.Instant

import cats.effect.IO

import oauth4s.*
import oauth4s.http.HttpClient
import oauth4s.http.jdk.JdkHttpClient

import oauth4s.runtime.cats.CatsInstances.given

given HttpClient[IO] = JdkHttpClient[IO]()

val endpoints = Endpoints.of(Endpoint("https://auth.example.com/authorize"), Endpoint("https://auth.example.com/token"))
val config    = OAuthClientConfig(ClientId("client-1"), ClientAuthentication.SecretBasic(ClientSecret("s3cret")), endpoints)
val client    = OAuthClient[IO](config.withScope(Scope.of("read", "write")))

// or from the server's own description: RFC 8414 first, the OpenID configuration second
val discovered: IO[OAuthClient[IO]] =
  OAuthClient.discover[IO](Issuer("https://auth.example.com"), ClientId("client-1"), ClientAuthentication.Public)

val redirect = RedirectUri("https://app.example.com/callback")

// the first request: remember the flow, send the browser to its url
def signIn(remember: AuthorizationFlow => IO[Unit]): IO[String] =
  for
    flow <- client.authorize(redirect, AuthorizationOptions.scoped("read").withPrompt("consent"))
    _    <- remember(flow)
  yield flow.url

// the second request: the provider sent the browser back to the redirect URI
def callback(flow: AuthorizationFlow, requestUrl: String): IO[TokenResponse] =
  client.complete(flow, AuthorizationCallback.fromUrl(requestUrl))

val tuned: OAuthClientConfig =
  config
    .withIssuer(Issuer("https://auth.example.com"))                    // compared with the `iss` of the callback, RFC 9207
    .withScope(Scope.of("read"))                                       // what a flow asks for when it names no scope
    .withScopeSeparator(",")                                           // for the providers that do not use a space
    .withAuthorizationParameter("audience", "https://api.example.com") // on every authorization request
    .withTokenParameter("audience", "https://api.example.com")         // on every token request
    .forwardingCallbackParameter("device_id")                          // from the callback into the code exchange
    .withUserAgent("billing/1.4")

val minted: Instant => ClientSecret = now => ClientSecret(s"signed-at-${now.getEpochSecond}")

val authentication: List[ClientAuthentication] =
  List(
    ClientAuthentication.Public,                              // a browser, a mobile or a desktop application
    ClientAuthentication.SecretBasic(ClientSecret("s3cret")), // Authorization: Basic, what RFC 6749 prefers
    ClientAuthentication.SecretPost(ClientSecret("s3cret")),  // client_id and client_secret in the form
    ClientAuthentication.SecretMinted(minted),                // a secret made for each request, from the time it is made at
  )

val options: AuthorizationOptions =
  AuthorizationOptions.default
    .withScope(Scope.of("read", "write"))
    .withNonce                 // a nonce in the request and in the flow
    .withPrompt("consent")
    .withLoginHint("ann@example.com")
    .withParameter("ui_locales", "ru")
    .withPkce(PkceMethod.S256) // the default; withoutPkce for a provider that refuses the parameters

def posted(flow: AuthorizationFlow, requestBody: String): IO[TokenResponse] =
  client.complete(flow, AuthorizationCallback.fromForm(requestBody)) // response_mode=form_post

def denied(requestUrl: String): Option[OAuthError] =
  AuthorizationCallback.fromUrl(requestUrl).error // Some(Rejected(AccessDenied, …)) when the user said no
