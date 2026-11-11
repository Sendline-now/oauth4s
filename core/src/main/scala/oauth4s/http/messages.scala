package oauth4s.http

import scala.annotation.targetName

enum HttpMethod(val name: String):
  case Get  extends HttpMethod("GET")
  case Post extends HttpMethod("POST")

opaque type HttpHeaders = Vector[(String, String)]

object HttpHeaders:
  val empty: HttpHeaders = Vector.empty

  def of(values: (String, String)*): HttpHeaders = values.toVector

  def from(values: Iterable[(String, String)]): HttpHeaders = values.toVector

  extension (headers: HttpHeaders)
    inline def toVector: Vector[(String, String)]     = headers
    def get(name: String): Option[String]             = headers.collectFirst { case (key, value) if key.equalsIgnoreCase(name) => value }
    def all(name: String): Vector[String]             = headers.collect { case (key, value) if key.equalsIgnoreCase(name) => value }
    def add(name: String, value: String): HttpHeaders = headers :+ (name -> value)

    @targetName("concat")
    def ++(other: HttpHeaders): HttpHeaders = headers ++ other

final case class HttpRequest(method: HttpMethod, url: String, headers: HttpHeaders, body: Option[String]):
  def header(name: String): Option[String] = headers.get(name)

object HttpRequest:
  def get(url: String, headers: (String, String)*): HttpRequest = HttpRequest(HttpMethod.Get, url, HttpHeaders.of(headers*), None)

  def post(url: String, body: String, headers: (String, String)*): HttpRequest = HttpRequest(HttpMethod.Post, url, HttpHeaders.of(headers*), Some(body))

final case class HttpResponse(status: Int, headers: HttpHeaders, body: String):
  def isSuccess: Boolean                   = status >= 200 && status < 300
  def header(name: String): Option[String] = headers.get(name)

object HttpResponse:
  def of(status: Int, body: String, headers: (String, String)*): HttpResponse = HttpResponse(status, HttpHeaders.of(headers*), body)

  def json(body: String, status: Int = 200): HttpResponse = of(status, body, "Content-Type" -> "application/json")
