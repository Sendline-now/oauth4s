package oauth4s.it.docs.recipes.guard

import cats.effect.IO

import oauth4s.http.HttpClient
import oauth4s.{Endpoint, Scope}
import oauth4s.jwt.{JwksSource, Jwt, JwtException, JwtValidation, JwtVerifier}

import oauth4s.runtime.cats.CatsInstances.given

final class Guard(keys: JwksSource[IO], rules: JwtValidation):

  def allow(authorization: Option[String], scope: String): IO[Either[String, Jwt]] =
    authorization.collect { case s"Bearer $token" => token } match
      case None        => IO.pure(Left("the request carries no bearer token"))
      case Some(token) =>
        JwtVerifier.verify[IO](token, keys, rules).attempt.map {
          case Right(jwt) if granted(jwt).contains(scope) => Right(jwt)
          case Right(_)                                   => Left(s"the token was not granted $scope")
          case Left(refused: JwtException)                => Left(refused.error.message)
          case Left(other)                                => Left(s"the keys could not be read: ${other.getMessage}")
        }

  private def granted(jwt: Jwt): Scope = Scope.parse(jwt.claims.string("scope").getOrElse(""))

object Guard:
  def of(issuer: String, audience: String, jwks: Endpoint)(using HttpClient[IO]): Guard =
    Guard(JwksSource.remote[IO](jwks), JwtValidation.of(issuer, audience))
