package oauth4s.http.sttp

import scala.util.Try

import sttp.client4.DefaultSyncBackend
import sttp.client4.wrappers.TryBackend

import oauth4s.http.HttpClient
import oauth4s.testkit.HttpClientSuite

final class SttpHttpClientSpec extends HttpClientSuite[Try]("sttp"):
  protected val client: HttpClient[Try]     = SttpHttpClient(TryBackend(DefaultSyncBackend()))
  protected def unsafeRun[A](fa: Try[A]): A = fa.get
