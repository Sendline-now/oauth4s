package oauth4s

enum ErrorCode(val value: String):
  case InvalidRequest          extends ErrorCode("invalid_request")
  case InvalidClient           extends ErrorCode("invalid_client")
  case InvalidGrant            extends ErrorCode("invalid_grant")
  case UnauthorizedClient      extends ErrorCode("unauthorized_client")
  case UnsupportedGrantType    extends ErrorCode("unsupported_grant_type")
  case UnsupportedResponseType extends ErrorCode("unsupported_response_type")
  case UnsupportedTokenType    extends ErrorCode("unsupported_token_type")
  case InvalidScope            extends ErrorCode("invalid_scope")
  case InvalidTarget           extends ErrorCode("invalid_target")
  case InvalidToken            extends ErrorCode("invalid_token")
  case InsufficientScope       extends ErrorCode("insufficient_scope")
  case AccessDenied            extends ErrorCode("access_denied")
  case ServerError             extends ErrorCode("server_error")
  case TemporarilyUnavailable  extends ErrorCode("temporarily_unavailable")
  case AuthorizationPending    extends ErrorCode("authorization_pending")
  case SlowDown                extends ErrorCode("slow_down")
  case ExpiredToken            extends ErrorCode("expired_token")
  case InteractionRequired     extends ErrorCode("interaction_required")
  case LoginRequired           extends ErrorCode("login_required")
  case ConsentRequired         extends ErrorCode("consent_required")
  case AccountSelection        extends ErrorCode("account_selection_required")
  case Other(raw: String)      extends ErrorCode(raw)

object ErrorCode:
  private val registered: Map[String, ErrorCode] =
    List(
      InvalidRequest,
      InvalidClient,
      InvalidGrant,
      UnauthorizedClient,
      UnsupportedGrantType,
      UnsupportedResponseType,
      UnsupportedTokenType,
      InvalidScope,
      InvalidTarget,
      InvalidToken,
      InsufficientScope,
      AccessDenied,
      ServerError,
      TemporarilyUnavailable,
      AuthorizationPending,
      SlowDown,
      ExpiredToken,
      InteractionRequired,
      LoginRequired,
      ConsentRequired,
      AccountSelection,
    ).map(code => code.value -> code).toMap

  def parse(raw: String): ErrorCode = registered.getOrElse(raw, Other(raw))

enum OAuthError:
  case Rejected(code: ErrorCode, description: Option[String], uri: Option[String])
  case UnexpectedStatus(status: Int, body: String)
  case MalformedResponse(reason: String)
  case MissingField(name: String)
  case MissingEndpoint(name: String)
  case StateMismatch(expected: State, received: Option[State])
  case IssuerMismatch(expected: Issuer, received: String)
  case InvalidUrl(url: String, reason: String)
  case InvalidCodeVerifier(reason: String)
  case Transport(cause: Throwable)

  def message: String =
    this match
      case Rejected(code, description, _)     => s"the server answered ${code.value}${description.fold("")(text => s": $text")}"
      case UnexpectedStatus(status, body)     => s"the server answered HTTP $status${Option(body).filter(_.nonEmpty).fold("")(text => s": $text")}"
      case MalformedResponse(reason)          => s"the response is malformed: $reason"
      case MissingField(name)                 => s"'$name' is missing"
      case MissingEndpoint(name)              => s"this client has no $name endpoint; give it one in Endpoints, or build the client from the server's metadata"
      case StateMismatch(expected, received)  =>
        s"the callback carries ${received.fold("no state")(state => s"state '${state.value}'")} but the flow was started with '${expected.value}'"
      case IssuerMismatch(expected, received) => s"the server answers as issuer '$received' but '${expected.value}' was asked"
      case InvalidUrl(url, reason)            => s"'$url' cannot be used: $reason"
      case InvalidCodeVerifier(reason)        => s"the PKCE code verifier is invalid: $reason"
      case Transport(cause)                   => s"the request did not complete: ${Option(cause.getMessage).getOrElse(cause.getClass.getName)}"

  def isPending: Boolean =
    this match
      case Rejected(ErrorCode.AuthorizationPending | ErrorCode.SlowDown, _, _) => true
      case _                                                                   => false

  def toThrowable: OAuthException = OAuthException(this)

object OAuthError:
  def rejected(code: ErrorCode, description: String): OAuthError = Rejected(code, Some(description), None)

abstract class OAuth4sException(message: String, cause: Throwable) extends RuntimeException(message, cause)

final class OAuthException(val error: OAuthError) extends OAuth4sException(
      error.message,
      error match
        case OAuthError.Transport(cause) => cause
        case _                           => null,
    )
