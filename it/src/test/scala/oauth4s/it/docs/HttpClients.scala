package oauth4s.it.docs.http

import java.net.http.HttpClient as JavaHttpClient

import cats.effect.IO
import sttp.client4.Backend
import org.http4s.client.Client

import oauth4s.Effect
import oauth4s.http.jdk.JdkHttpClient
import oauth4s.http.sttp.SttpHttpClient
import oauth4s.http.http4s.Http4sHttpClient
import oauth4s.http.{HttpClient, HttpRequest, HttpResponse}

import scala.concurrent.duration.given
import oauth4s.runtime.cats.CatsInstances.given

val jdk: HttpClient[IO] = JdkHttpClient[IO](timeout = 10.seconds)

def shared(client: JavaHttpClient): HttpClient[IO] = JdkHttpClient.from[IO](client) // one you configured: a proxy, an SSL context

def onHttp4s(client: Client[IO]): HttpClient[IO] = Http4sHttpClient(client) // ember, blaze, the JDK client — any http4s Client

def onSttp(backend: Backend[IO]): HttpClient[IO] = SttpHttpClient(backend) // any sttp client 4 backend

final class Counted[F[*]](underlying: HttpClient[F], count: String => Unit)(using F: Effect[F]) extends HttpClient[F]:
  def send(request: HttpRequest): F[HttpResponse] =
    F.map(underlying.send(request)) { response =>
      count(s"${request.method.name} ${response.status}") // never the body: it carries the secret
      response
    }
