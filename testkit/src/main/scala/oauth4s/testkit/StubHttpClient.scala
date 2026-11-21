package oauth4s.testkit

import java.util.concurrent.atomic.AtomicReference

import oauth4s.Effect
import oauth4s.internal.Urls
import oauth4s.http.{HttpClient, HttpRequest, HttpResponse}

final class StubHttpClient[F[*]] private (handler: HttpRequest => F[HttpResponse]) extends HttpClient[F]:
  private val recorded = AtomicReference(Vector.empty[HttpRequest])

  def send(request: HttpRequest): F[HttpResponse] =
    recorded.updateAndGet(_ :+ request)
    handler(request)

  def requests: Vector[HttpRequest] = recorded.get

  def lastRequest: Option[HttpRequest] = recorded.get.lastOption

  def lastForm: Map[String, String] = lastRequest.fold(Map.empty)(StubHttpClient.form)

object StubHttpClient:
  val NotFound: HttpResponse = HttpResponse.json("""{"error":"not_found"}""", 404)

  def apply[F[*]](handler: HttpRequest => F[HttpResponse]): StubHttpClient[F] = new StubHttpClient[F](handler)

  def routes[F[*]](routes: (String, HttpResponse)*)(using F: Effect[F]): StubHttpClient[F] =
    val table = routes.toMap

    apply(request => F.pure(table.getOrElse(request.url.takeWhile(_ != '?'), NotFound)))

  def routesBy[F[*]](route: PartialFunction[HttpRequest, HttpResponse])(using F: Effect[F]): StubHttpClient[F] =
    apply(request => F.delay(route.applyOrElse(request, _ => NotFound)))

  def sequence[F[*]](responses: HttpResponse*)(using F: Effect[F]): StubHttpClient[F] =
    val remaining = AtomicReference(responses.toVector)

    apply(_ => F.delay(remaining.getAndUpdate(_.drop(1)).headOption.getOrElse(NotFound)))

  def failing[F[*]](cause: Throwable)(using F: Effect[F]): StubHttpClient[F] = apply(_ => F.raiseError(cause))

  def form(request: HttpRequest): Map[String, String] = request.body.fold(Map.empty)(body => Urls.form(body).toMap)

  def query(request: HttpRequest): Map[String, String] = Urls.queryOf(request.url).toMap
