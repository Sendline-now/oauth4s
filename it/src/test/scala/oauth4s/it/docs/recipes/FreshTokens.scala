package oauth4s.it.docs.recipes.fresh

import cats.effect.IO

import oauth4s.{AccessToken, TokenSource}

def withToken[A](tokens: TokenSource[IO])(call: AccessToken => IO[Either[Int, A]]): IO[A] =
  val once = tokens.accessToken.flatMap(call)

  once.flatMap {
    case Left(401) => tokens.invalidate >> once // revoked before it expired: one more try, with a new token
    case answer    => IO.pure(answer)
  }.flatMap(answer => IO.fromEither(answer.left.map(status => IllegalStateException(s"the API answered HTTP $status"))))
