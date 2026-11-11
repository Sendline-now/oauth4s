package oauth4s.it.docs.errors

import cats.effect.IO

import oauth4s.*
import oauth4s.jwt.{JwtError, JwtException}
import oauth4s.oidc.{OidcClient, OidcTokens}

def renewed(client: OAuthClient[IO], token: RefreshToken): IO[Option[TokenResponse]] =
  client.refresh(token).map(Option(_)).recoverWith { case failure: OAuthException =>
    failure.error match
      case OAuthError.Rejected(ErrorCode.InvalidGrant, _, _) => IO.none              // revoked or expired: sign in again
      case OAuthError.Transport(cause)                       => IO.raiseError(cause) // the network: safe to try again
      case other                                             => IO.raiseError(IllegalStateException(other.message))
  }

def signedIn(oidc: OidcClient[IO], flow: AuthorizationFlow, callback: AuthorizationCallback): IO[Either[String, OidcTokens]] =
  oidc.complete(flow, callback).attempt.map {
    case Right(tokens)                                                          => Right(tokens)
    case Left(failure: JwtException) if failure.error == JwtError.NonceMismatch => Left("this sign-in was started somewhere else")
    case Left(failure: JwtException)                                            => Left(s"the ID token was refused: ${failure.error.message}")
    case Left(failure: OAuthException)                                          => Left(failure.error.message)
    case Left(other)                                                            => Left(other.getMessage)
  }
