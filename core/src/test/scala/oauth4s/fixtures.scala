package oauth4s

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

import scala.util.Try
import scala.concurrent.duration.FiniteDuration

import oauth4s.internal.Urls
import oauth4s.http.{HttpClient, HttpRequest, HttpResponse}

final class ManualTime(start: Instant = ManualTime.Start) extends Effect[Try]:
  private val clock = AtomicReference(start)
  private val naps  = AtomicReference(Vector.empty[FiniteDuration])

  def pure[A](a: A): Try[A]                                          = Effect.blocking.pure(a)
  def delay[A](a: => A): Try[A]                                      = Effect.blocking.delay(a)
  def map[A, B](fa: Try[A])(f: A => B): Try[B]                       = Effect.blocking.map(fa)(f)
  def flatMap[A, B](fa: Try[A])(f: A => Try[B]): Try[B]              = Effect.blocking.flatMap(fa)(f)
  def raiseError[A](error: Throwable): Try[A]                        = Effect.blocking.raiseError(error)
  def handleErrorWith[A](fa: Try[A])(f: Throwable => Try[A]): Try[A] = Effect.blocking.handleErrorWith(fa)(f)

  def sleep(duration: FiniteDuration): Try[Unit] =
    Try {
      naps.updateAndGet(_ :+ duration)
      advance(duration)
    }

  override def realTime: Try[Instant]            = Try(clock.get)
  override def suspend[A](fa: => Try[A]): Try[A] = Effect.blocking.suspend(fa)

  def now: Instant = clock.get

  def advance(duration: FiniteDuration): Unit = clock.updateAndGet(_.plusMillis(duration.toMillis)): Unit

  def sleeps: Vector[FiniteDuration] = naps.get

object ManualTime:
  val Start: Instant = Instant.parse("2026-01-01T00:00:00Z")

final class RecordingHttp(handler: HttpRequest => HttpResponse) extends HttpClient[Try]:
  private val recorded = AtomicReference(Vector.empty[HttpRequest])

  def send(request: HttpRequest): Try[HttpResponse] =
    Try {
      recorded.updateAndGet(_ :+ request)
      handler(request)
    }

  def requests: Vector[HttpRequest] = recorded.get

  def last: HttpRequest = recorded.get.last

  def lastForm: Map[String, String] = last.body.fold(Map.empty)(body => Urls.form(body).toMap)

  def lastQuery: Map[String, String] = Urls.queryOf(last.url).toMap

object RecordingHttp:
  val NotFound: HttpResponse = HttpResponse.json("""{"error":"not_found"}""", 404)

  def routes(routes: (String, HttpResponse)*): RecordingHttp =
    val table = routes.toMap

    RecordingHttp(request => table.getOrElse(request.url.takeWhile(_ != '?'), NotFound))

  def sequence(responses: HttpResponse*): RecordingHttp =
    val remaining = AtomicReference(responses.toVector)

    RecordingHttp(_ => remaining.getAndUpdate(_.drop(1)).headOption.getOrElse(NotFound))

  def failing(cause: Throwable): RecordingHttp = RecordingHttp(_ => throw cause)

object Fixtures:
  val Issuer: Issuer             = oauth4s.Issuer("https://auth.example.com")
  val Authorization: Endpoint    = Endpoint("https://auth.example.com/authorize")
  val Token: Endpoint            = Endpoint("https://auth.example.com/token")
  val Device: Endpoint           = Endpoint("https://auth.example.com/device")
  val Revocation: Endpoint       = Endpoint("https://auth.example.com/revoke")
  val Introspection: Endpoint    = Endpoint("https://auth.example.com/introspect")
  val Redirect: RedirectUri      = RedirectUri("https://app.example.com/callback")
  val ClientId: ClientId         = oauth4s.ClientId("client-1")
  val ClientSecret: ClientSecret = oauth4s.ClientSecret("s3cret")

  val Endpoints: Endpoints =
    oauth4s.Endpoints.of(Authorization, Token).withDeviceAuthorization(Device).withRevocation(Revocation).withIntrospection(Introspection)

  val Config: OAuthClientConfig = OAuthClientConfig(ClientId, ClientAuthentication.SecretBasic(ClientSecret), Endpoints)

  val TokenJson: String =
    """{"access_token":"access-1","token_type":"Bearer","expires_in":3600,"refresh_token":"refresh-1","scope":"read write"}"""

  val TokenReply: HttpResponse = HttpResponse.json(TokenJson)

  def rejected(code: String, description: String = "", status: Int = 400): HttpResponse =
    HttpResponse.json(s"""{"error":"$code","error_description":"$description"}""", status)
