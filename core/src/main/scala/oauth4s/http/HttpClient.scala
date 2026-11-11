package oauth4s.http

import scala.annotation.implicitNotFound

@implicitNotFound(
  "No HttpClient[${F}]. Build one from the HTTP client you use: `given HttpClient[F] = JdkHttpClient[F]()` from oauth4s-http-jdk, " +
    "`Http4sHttpClient(client)` from oauth4s-http4s, `SttpHttpClient(backend)` from oauth4s-sttp, or `StubHttpClient` from oauth4s-testkit in a test."
)
trait HttpClient[F[*]]:
  def send(request: HttpRequest): F[HttpResponse]

object HttpClient:
  inline def apply[F[*]](using client: HttpClient[F]): client.type = client
