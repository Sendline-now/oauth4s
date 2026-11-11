package oauth4s

import java.time.Instant

enum GrantType(val value: String):
  case AuthorizationCode  extends GrantType("authorization_code")
  case ClientCredentials  extends GrantType("client_credentials")
  case RefreshToken       extends GrantType("refresh_token")
  case DeviceCode         extends GrantType("urn:ietf:params:oauth:grant-type:device_code")
  case JwtBearer          extends GrantType("urn:ietf:params:oauth:grant-type:jwt-bearer")
  case TokenExchange      extends GrantType("urn:ietf:params:oauth:grant-type:token-exchange")
  case Other(raw: String) extends GrantType(raw)

enum TokenType(val value: String):
  case Bearer             extends TokenType("Bearer")
  case DPoP               extends TokenType("DPoP")
  case Other(raw: String) extends TokenType(raw)

object TokenType:
  def parse(raw: String): TokenType =
    if raw.equalsIgnoreCase(Bearer.value) then Bearer
    else if raw.equalsIgnoreCase(DPoP.value) then DPoP
    else Other(raw)

enum TokenTypeHint(val value: String):
  case AccessToken  extends TokenTypeHint("access_token")
  case RefreshToken extends TokenTypeHint("refresh_token")

enum ClientAuthentication:
  case Public
  case SecretBasic(secret: ClientSecret)
  case SecretPost(secret: ClientSecret)
  case SecretMinted(mint: Instant => ClientSecret)

  def method: String =
    this match
      case Public          => "none"
      case SecretBasic(_)  => "client_secret_basic"
      case SecretPost(_)   => "client_secret_post"
      case SecretMinted(_) => "client_secret_post"

  override def toString: String = s"ClientAuthentication($method)"
