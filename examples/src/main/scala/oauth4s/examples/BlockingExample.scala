package oauth4s.examples

import scala.util.Try

import oauth4s.*
import oauth4s.http.HttpClient
import oauth4s.http.jdk.JdkHttpClient
import oauth4s.testkit.FakeAuthorizationServer

object BlockingExample:

  def main(arguments: Array[String]): Unit =
    val server = FakeAuthorizationServer.start()

    given HttpClient[Try] = JdkHttpClient[Try]()

    val program =
      for
        client <- OAuthClient.discover[Try](server.issuer, server.clientId, ClientAuthentication.SecretBasic(server.clientSecret))
        source  = TokenSource.clientCredentials(client, Scope.of("read"))
        first  <- source.token
        again  <- source.token
      yield println(s"blocking: ${first.tokenType.value} token for ${first.scope.fold("")(_.render)}, asked once: ${first eq again}")

    server.close()
    program.get
