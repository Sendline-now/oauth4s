package oauth4s.it.docs.recipes.sessions

import cats.effect.IO
import io.circe.syntax.*
import io.circe.parser.decode

import oauth4s.{AccessToken, OAuthClient, TokenResponse, TokenSource}

import oauth4s.json.circe.CirceInstances.given
import oauth4s.runtime.cats.CatsInstances.given

final class Sessions(client: OAuthClient[IO], load: String => IO[Option[String]], save: (String, String) => IO[Unit]):

  def signedIn(user: String, tokens: TokenResponse): IO[Unit] = save(user, tokens.asJson.noSpaces)

  def accessToken(user: String): IO[Option[AccessToken]] =
    load(user).flatMap {
      case None       => IO.none
      case Some(text) =>
        for
          stored <- IO.fromEither(decode[TokenResponse](text))
          fresh  <- TokenSource.refreshing(client, stored).token // the stored token while it is valid, a refreshed one after
          _      <- IO.whenA(fresh ne stored)(save(user, fresh.asJson.noSpaces))
        yield Some(fresh.accessToken)
    }
