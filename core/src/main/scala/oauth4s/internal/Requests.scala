package oauth4s.internal

import oauth4s.json.{Json, JsonCodec}
import oauth4s.{AccessToken, Effect, OAuthError}
import oauth4s.http.{HttpClient, HttpHeaders, HttpMethod, HttpRequest, HttpResponse}

private[oauth4s] object Requests:
  private val Accept = HttpHeaders.of("Accept" -> "application/json")
  private val Form   = HttpHeaders.of("Content-Type" -> "application/x-www-form-urlencoded")

  def fail[F[*], A](error: OAuthError)(using F: Effect[F]): F[A] = F.raiseError(error.toThrowable)

  def lift[F[*], A](either: Either[OAuthError, A])(using F: Effect[F]): F[A] = either.fold(fail[F, A], F.pure)

  def bearer(token: AccessToken): HttpHeaders = HttpHeaders.of("Authorization" -> s"Bearer ${token.value}")

  def send[F[*]](request: HttpRequest)(using F: Effect[F], http: HttpClient[F]): F[HttpResponse] =
    F.handleErrorWith(F.suspend(http.send(request)))(cause => fail(OAuthError.Transport(cause)))

  def get[F[*]](url: String, parameters: Iterable[(String, String)], headers: HttpHeaders)(using Effect[F], HttpClient[F]): F[HttpResponse] =
    send(HttpRequest(HttpMethod.Get, Urls.withQuery(url, parameters), Accept ++ headers, None))

  def post[F[*]](url: String, form: Iterable[(String, String)], headers: HttpHeaders)(using Effect[F], HttpClient[F]): F[HttpResponse] =
    send(HttpRequest(HttpMethod.Post, url, Accept ++ Form ++ headers, Some(Urls.formEncode(form))))

  def getJson[F[*]](url: String, parameters: Iterable[(String, String)], headers: HttpHeaders)(using F: Effect[F], http: HttpClient[F], codec: JsonCodec): F[Json] =
    F.flatMap(get(url, parameters, headers))(response => lift(Responses.decode(response)(Right(_))))

  def postJson[F[*]](url: String, form: Iterable[(String, String)], headers: HttpHeaders)(using F: Effect[F], http: HttpClient[F], codec: JsonCodec): F[Json] =
    F.flatMap(post(url, form, headers))(response => lift(Responses.decode(response)(Right(_))))
