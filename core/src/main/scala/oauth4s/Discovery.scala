package oauth4s

import oauth4s.json.JsonCodec
import oauth4s.http.{HttpClient, HttpHeaders}
import oauth4s.internal.{Requests, Responses, Urls}

object Discovery:
  val OAuthDocument: String  = "oauth-authorization-server"
  val OpenIdDocument: String = "openid-configuration"

  def oauth[F[*]](issuer: Issuer)(using Effect[F], HttpClient[F], JsonCodec): F[ServerMetadata] =
    fetch(issuer, Urls.wellKnown(issuer, OAuthDocument, inserted = true))

  def openid[F[*]](issuer: Issuer)(using Effect[F], HttpClient[F], JsonCodec): F[ServerMetadata] =
    fetch(issuer, Urls.wellKnown(issuer, OpenIdDocument, inserted = false))

  def discover[F[*]](issuer: Issuer)(using F: Effect[F], http: HttpClient[F], codec: JsonCodec): F[ServerMetadata] =
    F.handleErrorWith(oauth[F](issuer)) {
      case first: OAuthException => F.handleErrorWith(openid[F](issuer))(_ => F.raiseError(first))
      case other                 => F.raiseError(other)
    }

  private def fetch[F[*]](issuer: Issuer, url: Either[OAuthError, String])(using F: Effect[F], http: HttpClient[F], codec: JsonCodec): F[ServerMetadata] =
    F.flatMap(Requests.lift(url)) { location =>
      F.flatMap(Requests.get(location, Nil, HttpHeaders.empty)) { response =>
        Requests.lift(Responses.decode(response)(ServerMetadata.fromJson).flatMap(sameIssuer(issuer, _)))
      }
    }

  private def sameIssuer(issuer: Issuer, metadata: ServerMetadata): Either[OAuthError, ServerMetadata] =
    Either.cond(metadata.issuer.value == issuer.value, metadata, OAuthError.IssuerMismatch(issuer, metadata.issuer.value))
