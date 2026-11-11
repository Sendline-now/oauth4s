package oauth4s.it.docs.providers

import cats.effect.IO

import oauth4s.*
import oauth4s.jwt.JwtSigner
import oauth4s.http.HttpClient
import oauth4s.http.jdk.JdkHttpClient
import oauth4s.providers.{Apple, Facebook, Google, Instagram, Login, Microsoft, Provider, Strava, UserProfile, VkId, X}

import oauth4s.runtime.cats.CatsInstances.given

given HttpClient[IO] = JdkHttpClient[IO]()

val id       = ClientId("client-id")
val secret   = ClientSecret("client-secret")
val redirect = RedirectUri("https://app.example.com/callback")

val google: Provider[IO] = Google[IO](id, secret)

val withRefreshToken: IO[AuthorizationFlow] =
  google.authorize(redirect, Google.Offline) // access_type=offline and prompt=consent

val microsoft: Provider[IO] = Microsoft[IO](id, secret)                          // Microsoft.Common: any account
val workOnly: Provider[IO]  = Microsoft[IO](id, secret, Microsoft.Organizations) // or Microsoft.Consumers, or a tenant id

def apple(p8: String): Provider[IO] =
  val key = JwtSigner.privateKey(p8, "EC").fold(error => throw error.toThrowable, identity)

  Apple[IO](ClientId("com.example.web"), "TEAM123456", "KEY1234567", key) // Services ID, Team ID, Key ID

def appleCallback(provider: Provider[IO], flow: AuthorizationFlow, requestBody: String): IO[Login] =
  provider.login(flow, AuthorizationCallback.fromForm(requestBody)) // Apple posts; `user` comes on the first sign-in only

val facebook: Provider[IO] = Facebook[IO](id, secret)

def sixtyDays(token: AccessToken): IO[TokenResponse] =
  Facebook.longLived(facebook, token) // fb_exchange_token

val instagram: Provider[IO] = Instagram[IO](id, secret)

def instagramSixtyDays(token: AccessToken): IO[TokenResponse] =
  Instagram.longLived[IO](secret, token) // ig_exchange_token, on graph.instagram.com

def instagramAgain(token: AccessToken): IO[TokenResponse] =
  Instagram.refreshed[IO](token) // ig_refresh_token, for a long-lived token that is at least a day old

val strava: Provider[IO] = Strava[IO](id, secret)

val activities: IO[AuthorizationFlow] =
  strava.authorize(redirect, AuthorizationOptions.scoped("read", "activity:read")) // sent as read,activity:read

val vk: Provider[IO] = VkId[IO](id)

def vkAgain(token: RefreshToken, callback: AuthorizationCallback, state: State): IO[TokenResponse] =
  VkId.refresh(vk, token, callback.parameter(VkId.DeviceId).getOrElse(""), state) // the device_id of the first callback

val x: Provider[IO]       = X[IO](id, secret) // a confidential client: Basic authentication
val xPublic: Provider[IO] = X.public[IO](id)  // a public client: PKCE alone

val xWithRefreshToken: IO[AuthorizationFlow] =
  x.authorize(redirect, AuthorizationOptions.default.withScope(X.Offline)) // adds offline.access

val olderGraph: Provider[IO] =
  Facebook.from(Facebook.config(id, secret, version = "v26.0").withScope(Scope.of("public_profile")))

def whoIs(provider: Provider[IO], tokens: TokenResponse): IO[UserProfile] =
  provider.profile(tokens) // the profile again, later, from tokens you kept

def revoke(tokens: TokenResponse): IO[Unit] =
  google.oauth.revokeAccessToken(tokens.accessToken) // the OAuth client underneath, for everything else
