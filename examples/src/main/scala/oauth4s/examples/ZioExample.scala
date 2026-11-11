package oauth4s.examples

import zio.{Console, Task, ZIO, ZIOAppDefault}

import oauth4s.*
import oauth4s.http.HttpClient
import oauth4s.http.jdk.JdkHttpClient
import oauth4s.testkit.FakeAuthorizationServer

import oauth4s.runtime.zio.ZioInstances.given

object ZioExample extends ZIOAppDefault:
  private given HttpClient[Task] = JdkHttpClient[Task]()

  def run: Task[Unit] =
    ZIO.acquireReleaseWith(ZIO.attemptBlocking(FakeAuthorizationServer.start()))(server => ZIO.succeed(server.close())) { server =>
      val client = OAuthClient[Task](OAuthClientConfig(server.clientId, ClientAuthentication.SecretPost(server.clientSecret), server.endpoints))

      for
        device <- client.deviceAuthorization(Scope.of("read"))
        _      <- Console.printLine(s"zio: enter ${device.userCode.value} at ${device.verificationUri}")
        _      <- ZIO.attemptBlocking(server.approveDevice(device.userCode))
        token  <- client.awaitDevice(device)
        _      <- Console.printLine(s"zio: the device was approved for ${token.scope.fold("")(_.render)}")
      yield ()
    }
