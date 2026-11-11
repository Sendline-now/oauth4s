package oauth4s

import oauth4s.internal.Urls

final class AuthorizationOptions private (
    val scope: Scope,
    val pkce: Option[PkceMethod],
    val nonce: Boolean,
    val parameters: Vector[(String, String)],
):
  def withScope(value: Scope): AuthorizationOptions = copy(scope = value)

  def withPkce(method: PkceMethod): AuthorizationOptions = copy(pkce = Some(method))

  def withoutPkce: AuthorizationOptions = copy(pkce = None)

  def withNonce: AuthorizationOptions = copy(nonce = true)

  def withParameter(name: String, value: String): AuthorizationOptions = copy(parameters = parameters :+ (name -> value))

  def withPrompt(value: String): AuthorizationOptions = withParameter("prompt", value)

  def withLoginHint(value: String): AuthorizationOptions = withParameter("login_hint", value)

  private def copy(
      scope: Scope = scope,
      pkce: Option[PkceMethod] = pkce,
      nonce: Boolean = nonce,
      parameters: Vector[(String, String)] = parameters,
  ): AuthorizationOptions =
    new AuthorizationOptions(
      scope = scope,
      pkce = pkce,
      nonce = nonce,
      parameters = parameters,
    )

object AuthorizationOptions:
  val default: AuthorizationOptions =
    new AuthorizationOptions(
      scope = Scope.empty,
      pkce = Some(PkceMethod.S256),
      nonce = false,
      parameters = Vector.empty,
    )

  def scoped(tokens: String*): AuthorizationOptions = default.withScope(Scope.of(tokens*))

final case class AuthorizationFlow(
    url: String,
    state: State,
    redirectUri: RedirectUri,
    verifier: Option[CodeVerifier],
    nonce: Option[Nonce],
)

opaque type AuthorizationCallback = Vector[(String, String)]

object AuthorizationCallback:
  def fromUrl(url: String): AuthorizationCallback = Urls.queryOf(url)

  def fromForm(body: String): AuthorizationCallback = Urls.form(body)

  def fromParameters(parameters: Iterable[(String, String)]): AuthorizationCallback = parameters.toVector

  extension (callback: AuthorizationCallback)
    inline def parameters: Vector[(String, String)] = callback
    def parameter(name: String): Option[String]     = callback.collectFirst { case (key, value) if key == name => value }
    def code: Option[AuthorizationCode]             = callback.parameter("code").filter(_.nonEmpty).map(AuthorizationCode(_))
    def state: Option[State]                        = callback.parameter("state").filter(_.nonEmpty).map(State(_))
    def issuer: Option[String]                      = callback.parameter("iss").filter(_.nonEmpty)

    def error: Option[OAuthError] =
      callback.parameter("error").map(code => OAuthError.Rejected(ErrorCode.parse(code), callback.parameter("error_description"), callback.parameter("error_uri")))
