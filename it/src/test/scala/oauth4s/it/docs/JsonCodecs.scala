package oauth4s.it.docs.json

import cats.effect.IO
import io.circe.Decoder
import io.circe.syntax.*
import io.circe.parser.decode

import oauth4s.oidc.{OidcClient, OidcTokens}
import oauth4s.json.{Json, JsonDecoder, JsonError}
import oauth4s.{AccessToken, Endpoint, OAuthClient, TokenResponse}

import oauth4s.json.circe.CirceInstances.given

final case class Me(sub: String, locale: Option[String]) derives Decoder

def me(oidc: OidcClient[IO], token: AccessToken): IO[Either[JsonError, Me]] =
  oidc.userInfo(token).map(_.decode[Me]) // with circe's Decoder[Me]

def claims(tokens: OidcTokens): Either[JsonError, Me] =
  tokens.idToken.claims.decode[Me]

def resource(client: OAuthClient[IO], token: AccessToken): IO[Either[JsonError, Me]] =
  client.resource(Endpoint("https://api.example.com/me"), token).map(_.as[Me])

def stored(token: TokenResponse): String = token.asJson.noSpaces // access_token, refresh_token, …, and when it was issued

def restored(text: String): Either[io.circe.Error, TokenResponse] = decode[TokenResponse](text)

object WithoutLibrary:
  final case class Plan(name: String, seats: Long)

  given JsonDecoder[Plan] = JsonDecoder.instance { json =>
    for
      name  <- json.string("name").toRight(JsonError.MissingField("name"))
      seats <- json.long("seats").toRight(JsonError.InvalidField("seats", "is not a whole number"))
    yield Plan(name, seats)
  }

  def plan(json: Json): Either[JsonError, Plan] = json.as[Plan]
