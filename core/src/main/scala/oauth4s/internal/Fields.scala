package oauth4s.internal

import java.time.Instant

import scala.util.Try
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

import oauth4s.json.Json
import oauth4s.{Endpoint, OAuthError}

private[oauth4s] object Fields:

  def required(json: Json, name: String): Either[OAuthError, String] =
    json.field(name) match
      case None        => Left(OAuthError.MissingField(name))
      case Some(value) => value.asString.filter(_.nonEmpty).toRight(OAuthError.MalformedResponse(s"'$name' is not a non-empty string"))

  def optional(json: Json, name: String): Option[String] = json.string(name).filter(_.nonEmpty)

  def seconds(json: Json, name: String): Either[OAuthError, Option[FiniteDuration]] =
    json.field(name).filter(_ != Json.Null) match
      case None        => Right(None)
      case Some(value) =>
        number(value).filter(_ >= 0) match
          case Some(seconds) => Right(Some(FiniteDuration((seconds * 1000).toLong, MILLISECONDS)))
          case None          => Left(OAuthError.MalformedResponse(s"'$name' is not a non-negative number of seconds"))

  def instant(json: Json, name: String): Option[Instant] =
    json.field(name).flatMap(number).map(seconds => Instant.ofEpochSecond(seconds.toLong))

  def endpoint(json: Json, name: String): Either[OAuthError, Option[Endpoint]] =
    optional(json, name) match
      case None        => Right(None)
      case Some(value) => Endpoint.parse(value).map(Some(_))

  def stringOrStrings(json: Json, name: String): Vector[String] =
    json.field(name) match
      case Some(Json.Str(value))  => Vector(value)
      case Some(Json.Arr(values)) => values.flatMap(_.asString)
      case _                      => Vector.empty

  private def number(value: Json): Option[BigDecimal] =
    value.asDecimal.orElse(value.asString.flatMap(text => Try(BigDecimal(text.trim)).toOption))
