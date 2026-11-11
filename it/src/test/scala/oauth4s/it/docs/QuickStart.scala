package oauth4s.it.docs.quickstart

import cats.effect.IO

import oauth4s.*
import oauth4s.http.HttpClient
import oauth4s.http.jdk.JdkHttpClient
import oauth4s.providers.{Google, Login}

import oauth4s.runtime.cats.CatsInstances.given

given HttpClient[IO] = JdkHttpClient[IO]()

val google   = Google[IO](ClientId("…apps.googleusercontent.com"), ClientSecret("…"))
val redirect = RedirectUri("https://app.example.com/callback")

// 1. send the browser to flow.url and keep the flow next to the user's session
val start: IO[AuthorizationFlow] = google.authorize(redirect)

// 2. the provider sends the browser back
def signedIn(flow: AuthorizationFlow, requestUrl: String): IO[Login] =
  google.login(flow, AuthorizationCallback.fromUrl(requestUrl))
