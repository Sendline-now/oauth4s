package oauth4s.it.docs.recipes.own

import java.nio.file.{Files, Path}

import cats.effect.IO

import oauth4s.*
import oauth4s.jwt.JwtSigner
import oauth4s.http.HttpClient
import oauth4s.oidc.OidcClient
import oauth4s.providers.{Apple, Provider, UserProfile}

import oauth4s.runtime.cats.CatsInstances.given

object GitHub:
  val Authorization: Endpoint = Endpoint("https://github.com/login/oauth/authorize")
  val Token: Endpoint         = Endpoint("https://github.com/login/oauth/access_token")
  val User: Endpoint          = Endpoint("https://api.github.com/user")

  def apply(clientId: ClientId, clientSecret: ClientSecret)(using HttpClient[IO]): Provider[IO] =
    val config = OAuthClientConfig(clientId, ClientAuthentication.SecretPost(clientSecret), Endpoints.of(Authorization, Token))
      .withScope(Scope.of("read:user", "user:email"))
      .withUserAgent("my-service") // GitHub's API refuses a request without one

    Provider.oauth("github", OAuthClient[IO](config), User) { json =>
      json.long("id").toRight(OAuthError.MissingField("id")).map { id =>
        UserProfile("github", id.toString, json.string("name"), json.string("login"), json.string("email"), None, json.string("avatar_url"), json)
      }
    }

def keycloak(clientId: ClientId, clientSecret: ClientSecret)(using HttpClient[IO]): IO[Provider[IO]] =
  OidcClient
    .discover[IO](Issuer("https://sso.example.com/realms/main"), clientId, ClientAuthentication.SecretBasic(clientSecret))
    .map(Provider.oidc("keycloak", _)) // the profile is the UserInfo response

def apple(p8: Path)(using HttpClient[IO]): IO[Provider[IO]] =
  for
    pem <- IO.blocking(Files.readString(p8))
    key <- IO.fromEither(JwtSigner.privateKey(pem, "EC").left.map(_.toThrowable))
  yield Apple[IO](ClientId("com.example.web"), "TEAM123456", "KEY1234567", key) // Services ID, Team ID, Key ID
