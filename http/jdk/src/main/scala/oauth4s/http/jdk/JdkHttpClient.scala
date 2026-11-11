package oauth4s.http.jdk

import java.net.URI
import java.time.Duration
import java.nio.charset.StandardCharsets
import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.net.http.{HttpClient as JavaHttpClient, HttpRequest as JavaHttpRequest, HttpResponse as JavaHttpResponse}

import scala.concurrent.duration.{FiniteDuration, SECONDS}

import oauth4s.Effect
import oauth4s.http.{HttpClient, HttpHeaders, HttpRequest, HttpResponse}

import scala.jdk.CollectionConverters.given

object JdkHttpClient:
  val DefaultTimeout: FiniteDuration = FiniteDuration(30, SECONDS)

  def apply[F[*]](timeout: FiniteDuration = DefaultTimeout)(using Effect[F]): HttpClient[F] =
    from(JavaHttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout.toMillis)).build(), timeout)

  def from[F[*]](client: JavaHttpClient, timeout: FiniteDuration = DefaultTimeout)(using F: Effect[F]): HttpClient[F] = new HttpClient[F]:
    def send(request: HttpRequest): F[HttpResponse] =
      F.blocking {
        val response = client.send(toJava(request, timeout), JavaHttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))

        HttpResponse(response.statusCode(), headersOf(response), response.body())
      }

  private def toJava(request: HttpRequest, timeout: FiniteDuration): JavaHttpRequest =
    val body: BodyPublisher = request.body.fold(BodyPublishers.noBody())(text => BodyPublishers.ofString(text, StandardCharsets.UTF_8))
    val builder             = JavaHttpRequest.newBuilder(URI.create(request.url)).timeout(Duration.ofMillis(timeout.toMillis)).method(request.method.name, body)

    request.headers.toVector.foreach((name, value) => builder.header(name, value): Unit)
    builder.build()

  private def headersOf(response: JavaHttpResponse[String]): HttpHeaders =
    HttpHeaders.from(response.headers().map().asScala.toVector.flatMap((name, values) => values.asScala.map(name -> _)))
