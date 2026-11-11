package oauth4s.internal

import oauth4s.http.HttpResponse
import oauth4s.json.{Json, JsonCodec}
import oauth4s.{ErrorCode, OAuthError}

private[oauth4s] object Responses:
  private val SnippetLength = 200

  def decode[A](response: HttpResponse)(f: Json => Either[OAuthError, A])(using JsonCodec): Either[OAuthError, A] =
    if response.isSuccess
    then json(response).flatMap(body => rejection(body).toLeft(body)).flatMap(f)
    else Left(error(response))

  def json(response: HttpResponse)(using codec: JsonCodec): Either[OAuthError, Json] =
    codec.parse(response.body).left.map(error => OAuthError.MalformedResponse(error.message))

  def error(response: HttpResponse)(using JsonCodec): OAuthError =
    json(response).toOption.flatMap(rejection).getOrElse(OAuthError.UnexpectedStatus(response.status, snippet(response.body)))

  private def rejection(body: Json): Option[OAuthError] =
    body.field("error").orElse(body.field("error_type")).flatMap {
      case Json.Str(code)   => Some(OAuthError.Rejected(ErrorCode.parse(code), described(body), body.string("error_uri")))
      case nested: Json.Obj =>
        nested.string("message").map(message => OAuthError.Rejected(ErrorCode.parse(nested.string("type").getOrElse("error")), Some(message), None))
      case _                => None
    }

  private def described(body: Json): Option[String] =
    body.string("error_description").orElse(body.string("error_message")).orElse(body.string("message"))

  private def snippet(body: String): String =
    if body.length <= SnippetLength
    then body
    else s"${body.take(SnippetLength)}..."
