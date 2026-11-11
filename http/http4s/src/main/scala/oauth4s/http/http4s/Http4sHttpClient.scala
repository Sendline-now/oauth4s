package oauth4s.http.http4s

import org.http4s.client.Client
import org.typelevel.ci.CIString
import cats.effect.kernel.Concurrent
import org.http4s.{Header, Method, Request, Uri}

import oauth4s.http.{HttpClient, HttpHeaders, HttpRequest, HttpResponse}

object Http4sHttpClient:

  def apply[F[*]](client: Client[F])(using F: Concurrent[F]): HttpClient[F] = new HttpClient[F]:
    def send(request: HttpRequest): F[HttpResponse] =
      F.flatMap(F.fromEither(toHttp4s[F](request))) { outgoing =>
        client.run(outgoing).use { response =>
          F.map(response.bodyText.compile.string) { body =>
            HttpResponse(response.status.code, HttpHeaders.from(response.headers.headers.map(header => header.name.toString -> header.value)), body)
          }
        }
      }

  private def toHttp4s[F[*]](request: HttpRequest): Either[Throwable, Request[F]] =
    for
      method <- Method.fromString(request.method.name)
      uri    <- Uri.fromString(request.url)
    yield
      val headers = request.headers.toVector.map((name, value) => Header.Raw(CIString(name), value))
      val bare    = Request[F](method = method, uri = uri)

      request.body.fold(bare)(text => bare.withEntity(text)).putHeaders(headers)
