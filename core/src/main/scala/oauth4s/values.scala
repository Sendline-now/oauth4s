package oauth4s

import oauth4s.internal.{Secrets, Urls}

opaque type ClientId = String

object ClientId:
  def apply(value: String): ClientId =
    require(value.nonEmpty, "a client id must not be empty")
    value

  extension (id: ClientId) inline def value: String = id

opaque type ClientSecret = String

object ClientSecret:
  def apply(value: String): ClientSecret =
    require(value.nonEmpty, "a client secret must not be empty — a client without one authenticates as ClientAuthentication.Public")
    value

  extension (secret: ClientSecret) inline def value: String = secret

opaque type RedirectUri = String

object RedirectUri:
  def apply(value: String): RedirectUri = value

  extension (uri: RedirectUri) inline def value: String = uri

opaque type Endpoint = String

object Endpoint:
  def apply(value: String): Endpoint =
    parse(value).fold(error => throw IllegalArgumentException(error.message), endpoint => endpoint)

  def parse(value: String): Either[OAuthError, Endpoint] = Urls.secure(value)

  extension (endpoint: Endpoint) inline def value: String = endpoint

opaque type Issuer = String

object Issuer:
  def apply(value: String): Issuer = value

  extension (issuer: Issuer) inline def value: String = issuer

opaque type AccessToken = String

object AccessToken:
  def apply(value: String): AccessToken = value

  extension (token: AccessToken) inline def value: String = token

opaque type RefreshToken = String

object RefreshToken:
  def apply(value: String): RefreshToken = value

  extension (token: RefreshToken) inline def value: String = token

opaque type IdToken = String

object IdToken:
  def apply(value: String): IdToken = value

  extension (token: IdToken) inline def value: String = token

opaque type AuthorizationCode = String

object AuthorizationCode:
  def apply(value: String): AuthorizationCode = value

  extension (code: AuthorizationCode) inline def value: String = code

opaque type DeviceCode = String

object DeviceCode:
  def apply(value: String): DeviceCode = value

  extension (code: DeviceCode) inline def value: String = code

opaque type UserCode = String

object UserCode:
  def apply(value: String): UserCode = value

  extension (code: UserCode) inline def value: String = code

opaque type State = String

object State:
  def apply(value: String): State = value

  def generate[F[*]](using F: Effect[F]): F[State] = F.delay(Secrets.token(Secrets.StateBytes))

  extension (state: State) inline def value: String = state

opaque type Nonce = String

object Nonce:
  def apply(value: String): Nonce = value

  def generate[F[*]](using F: Effect[F]): F[Nonce] = F.delay(Secrets.token(Secrets.NonceBytes))

  extension (nonce: Nonce) inline def value: String = nonce
