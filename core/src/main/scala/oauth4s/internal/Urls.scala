package oauth4s.internal

import java.nio.charset.StandardCharsets
import java.net.{URI, URISyntaxException, URLDecoder, URLEncoder}

import oauth4s.{Issuer, OAuthError}

private[oauth4s] object Urls:
  private val Hex = "0123456789ABCDEF"

  def secure(raw: String): Either[OAuthError, String] =
    parse(raw).flatMap { uri =>
      val scheme = Option(uri.getScheme).map(_.toLowerCase)
      val host   = Option(uri.getHost).map(_.toLowerCase)

      if host.isEmpty
      then Left(OAuthError.InvalidUrl(raw, "it must be an absolute URL"))
      else if uri.getRawFragment != null
      then Left(OAuthError.InvalidUrl(raw, "it must not have a fragment"))
      else if scheme.contains("https") || (scheme.contains("http") && host.exists(isLoopback))
      then Right(raw.trim)
      else Left(OAuthError.InvalidUrl(raw, "it must use https, or http on a loopback address"))
    }

  def wellKnown(issuer: Issuer, document: String, inserted: Boolean): Either[OAuthError, String] =
    parse(issuer.value).flatMap { uri =>
      val path = Option(uri.getRawPath).fold("")(_.stripSuffix("/"))

      if uri.getRawAuthority == null
      then Left(OAuthError.InvalidUrl(issuer.value, "it must be an absolute URL"))
      else if inserted
      then secure(s"${uri.getScheme}://${uri.getRawAuthority}/.well-known/$document$path")
      else secure(s"${uri.getScheme}://${uri.getRawAuthority}$path/.well-known/$document")
    }

  def encode(value: String): String =
    val bytes = value.getBytes(StandardCharsets.UTF_8)
    val out   = StringBuilder(bytes.length * 3)

    bytes.foreach { byte =>
      val char = (byte & 0xff).toChar

      if byte >= 0 && Secrets.isUnreserved(char)
      then out += char
      else
        out += '%'
        out += Hex.charAt((byte >> 4) & 0x0f)
        out += Hex.charAt(byte & 0x0f)
    }

    out.result()
  end encode

  def withQuery(url: String, parameters: Iterable[(String, String)]): String =
    val query = parameters.map((name, value) => s"${encode(name)}=${encode(value)}").mkString("&")

    if query.isEmpty then url
    else if url.contains('?') then s"$url&$query"
    else s"$url?$query"

  def queryOf(url: String): Vector[(String, String)] =
    val withoutFragment = url.takeWhile(_ != '#')
    val separator       = withoutFragment.indexOf('?')

    if separator < 0
    then Vector.empty
    else form(withoutFragment.substring(separator + 1))

  def form(body: String): Vector[(String, String)] =
    body
      .split('&')
      .iterator
      .filter(_.nonEmpty)
      .map { pair =>
        pair.split("=", 2) match
          case Array(name, value) => decode(name) -> decode(value)
          case _                  => decode(pair) -> ""
      }
      .toVector

  def formEncode(fields: Iterable[(String, String)]): String =
    fields.map((name, value) => s"${formValue(name)}=${formValue(value)}").mkString("&")

  def formValue(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

  def merge(parameters: Vector[(String, String)]): Vector[(String, String)] =
    parameters.zipWithIndex.collect { case ((name, value), index) if parameters.lastIndexWhere(_._1 == name) == index => name -> value }

  private def decode(value: String): String =
    try URLDecoder.decode(value, StandardCharsets.UTF_8)
    catch case _: IllegalArgumentException => value

  private def parse(raw: String): Either[OAuthError, URI] =
    try Right(URI(raw.trim))
    catch case _: URISyntaxException => Left(OAuthError.InvalidUrl(raw, "it is not a valid URL"))

  private def isLoopback(host: String): Boolean =
    host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host.endsWith(".localhost")
