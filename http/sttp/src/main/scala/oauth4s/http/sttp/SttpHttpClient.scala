package oauth4s.http.sttp

import sttp.model.{Header, Method, Uri}
import sttp.client4.{asStringAlways, basicRequest, Backend}

import oauth4s.http.{HttpClient, HttpHeaders, HttpRequest, HttpResponse}

object SttpHttpClient:

  def apply[F[*]](backend: Backend[F]): HttpClient[F] = new HttpClient[F]:
    def send(request: HttpRequest): F[HttpResponse] =
      val headers  = request.headers.toVector.map((name, value) => Header(name, value))
      val bare     = basicRequest.method(Method.unsafeApply(request.method.name), Uri.unsafeParse(request.url)).headers(headers*).response(asStringAlways)
      val outgoing = request.body.fold(bare)(text => bare.body(text))

      backend.monad.map(outgoing.send(backend)) { response =>
        HttpResponse(response.code.code, HttpHeaders.from(response.headers.map(header => header.name -> header.value)), response.body)
      }
