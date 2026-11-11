package oauth4s.it.docs.runtimes

import oauth4s.*

final class Billing[F[*]](client: OAuthClient[F])(using F: Effect[F]):
  private val tokens = TokenSource.clientCredentials(client, Scope.of("invoices:read"))

  def invoices(fetch: AccessToken => F[List[String]]): F[List[String]] =
    F.flatMap(tokens.accessToken)(fetch)

object OnCats:
  import cats.effect.IO

  import oauth4s.http.HttpClient
  import oauth4s.http.jdk.JdkHttpClient

  import oauth4s.runtime.cats.CatsInstances.given

  given HttpClient[IO] = JdkHttpClient[IO]()

  def billing(config: OAuthClientConfig): Billing[IO] = Billing(OAuthClient[IO](config))

object OnZio:
  import zio.Task

  import oauth4s.http.HttpClient
  import oauth4s.http.jdk.JdkHttpClient

  import oauth4s.runtime.zio.ZioInstances.given

  given HttpClient[Task] = JdkHttpClient[Task]()

  def billing(config: OAuthClientConfig): Billing[Task] = Billing(OAuthClient[Task](config))

object OnNothing:
  import scala.util.Try

  import oauth4s.http.HttpClient
  import oauth4s.http.jdk.JdkHttpClient

  given HttpClient[Try] = JdkHttpClient[Try]()

  def token(config: OAuthClientConfig): Try[TokenResponse] =
    OAuthClient[Try](config).clientCredentials() // the request has happened by the time this returns
