package oauth4s.examples

import cats.effect.{IO, IOApp, Resource}

import oauth4s.*
import oauth4s.http.HttpClient
import oauth4s.oidc.OidcClient
import oauth4s.http.jdk.JdkHttpClient
import oauth4s.testkit.FakeAuthorizationServer

import oauth4s.json.circe.CirceInstances.given
import oauth4s.runtime.cats.CatsInstances.given

object CatsEffectExample extends IOApp.Simple:
  private given HttpClient[IO] = JdkHttpClient[IO]()

  private val redirect = RedirectUri("http://127.0.0.1/callback")

  def run: IO[Unit] =
    Resource.make(IO.blocking(FakeAuthorizationServer.start()))(server => IO.blocking(server.close())).use { server =>
      for
        client <- OidcClient.discover[IO](server.issuer, server.clientId, ClientAuthentication.SecretBasic(server.clientSecret))
        flow   <- client.authorize(redirect, AuthorizationOptions.scoped("email"))
        landed <- IO.blocking(server.approve(flow.url))
        tokens <- client.complete(flow, AuthorizationCallback.fromUrl(landed))
        info   <- client.userInfo(tokens.response.accessToken)
        _      <- IO.println(s"cats-effect: ${tokens.user.subject.getOrElse("nobody")} signed in as ${info.email.getOrElse("unknown")}")
      yield ()
    }
