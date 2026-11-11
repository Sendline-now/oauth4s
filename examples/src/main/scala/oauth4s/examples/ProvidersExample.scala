package oauth4s.examples

import scala.util.Try

import oauth4s.http.HttpClient
import oauth4s.http.jdk.JdkHttpClient
import oauth4s.{ClientId, ClientSecret, RedirectUri}
import oauth4s.providers.{Facebook, Google, Microsoft, Provider, Strava, VkId, X}

object ProvidersExample:

  def main(arguments: Array[String]): Unit =
    given HttpClient[Try] = JdkHttpClient[Try]()

    val id       = ClientId("your-client-id")
    val secret   = ClientSecret("your-client-secret")
    val redirect = RedirectUri("https://app.example.com/callback")

    val providers: List[Provider[Try]] =
      List(Google[Try](id, secret), Microsoft[Try](id, secret), Facebook[Try](id, secret), Strava[Try](id, secret), VkId[Try](id), X[Try](id, secret))

    providers.foreach { provider =>
      val flow = provider.authorize(redirect).get

      println(s"providers: ${provider.name} -> ${flow.url.takeWhile(_ != '?')} (PKCE ${flow.verifier.fold("off")(_ => "on")})")
    }
