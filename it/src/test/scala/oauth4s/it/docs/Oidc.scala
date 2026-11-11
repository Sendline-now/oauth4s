package oauth4s.it.docs.oidc

import cats.effect.IO

import oauth4s.*
import oauth4s.jwt.Jwt
import oauth4s.http.HttpClient
import oauth4s.http.jdk.JdkHttpClient
import oauth4s.oidc.{OidcClient, OidcConfig, UserInfo}

import scala.concurrent.duration.given
import oauth4s.runtime.cats.CatsInstances.given

given HttpClient[IO] = JdkHttpClient[IO]()

val redirect = RedirectUri("https://app.example.com/callback")

val discovered: IO[OidcClient[IO]] =
  OidcClient.discover[IO](Issuer("https://auth.example.com"), ClientId("client-1"), ClientAuthentication.Public)

def start(oidc: OidcClient[IO]): IO[AuthorizationFlow] =
  oidc.authorize(redirect, AuthorizationOptions.scoped("email")) // openid and a nonce are added for you

def finish(oidc: OidcClient[IO], flow: AuthorizationFlow, requestUrl: String): IO[(Option[String], Option[String], UserInfo)] =
  for
    tokens <- oidc.complete(flow, AuthorizationCallback.fromUrl(requestUrl)) // exchanges the code, then verifies the ID token
    info   <- oidc.userInfo(tokens.response.accessToken)
  yield (tokens.user.subject, tokens.user.email, info)

def verified(oidc: OidcClient[IO], idToken: IdToken): IO[Jwt] =
  oidc.verify(idToken) // the checks of complete, for an ID token that arrived another way

def signOut(oidc: OidcClient[IO], idToken: IdToken): Either[OAuthError, String] =
  oidc.logoutUrl(Some(idToken), Some(RedirectUri("https://app.example.com/bye"))) // where to send the browser

def byHand(client: OAuthClientConfig): OidcConfig =
  OidcConfig(client, Issuer("https://auth.example.com"))
    .withKeysLifetime(6.hours)   // how long the provider's keys are kept
    .withKeysCooldown(5.minutes) // how often an unknown key id may cause a refetch
    .withValidation(_.withLeeway(30.seconds))
