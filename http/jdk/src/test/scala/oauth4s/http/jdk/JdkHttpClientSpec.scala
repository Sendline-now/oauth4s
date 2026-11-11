package oauth4s.http.jdk

import scala.util.Try

import oauth4s.http.HttpClient
import oauth4s.testkit.HttpClientSuite

final class JdkHttpClientSpec extends HttpClientSuite[Try]("java.net.http"):
  protected val client: HttpClient[Try]     = JdkHttpClient[Try]()
  protected def unsafeRun[A](fa: Try[A]): A = fa.get
