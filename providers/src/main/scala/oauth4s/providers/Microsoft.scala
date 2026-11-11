package oauth4s.providers

import oauth4s.*
import oauth4s.http.HttpClient
import oauth4s.json.{Json, JsonCodec}
import oauth4s.jwt.{JwtClaims, JwtError}
import oauth4s.oidc.{OidcClient, OidcConfig}
import oauth4s.providers.internal.ProviderImpl

object Microsoft:
  val Name: String          = "microsoft"
  val Common: String        = "common"
  val Consumers: String     = "consumers"
  val Organizations: String = "organizations"
  val DefaultScope: Scope   = Scope.of("openid", "profile", "email")

  val UserInfo: Endpoint = Endpoint("https://graph.microsoft.com/oidc/userinfo")

  private val Host        = "https://login.microsoftonline.com"
  private val MultiTenant = Set(Common, Consumers, Organizations)

  def issuer(tenantId: String): Issuer = Issuer(s"$Host/$tenantId/v2.0")

  def config(clientId: ClientId, clientSecret: ClientSecret, tenant: String = Common): OidcConfig =
    val endpoints =
      Endpoints
        .of(Endpoint(s"$Host/$tenant/oauth2/v2.0/authorize"), Endpoint(s"$Host/$tenant/oauth2/v2.0/token"))
        .withUserInfo(UserInfo)
        .withJwks(Endpoint(s"$Host/$tenant/discovery/v2.0/keys"))
        .withEndSession(Endpoint(s"$Host/$tenant/oauth2/v2.0/logout"))

    val client = OAuthClientConfig(clientId, ClientAuthentication.SecretPost(clientSecret), endpoints).withScope(DefaultScope)

    OidcConfig(client, issuer(tenant)).withValidation(_.withIssuer().withCheck(issuedBy(tenant)))

  def apply[F[*]](clientId: ClientId, clientSecret: ClientSecret, tenant: String = Common)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    from(config(clientId, clientSecret, tenant))

  def from[F[*]](config: OidcConfig)(using Effect[F], HttpClient[F], JsonCodec): Provider[F] =
    val client = OidcClient[F](config)
    val fetch  = ProviderImpl.fetched(client.oauth, UserInfo, Nil)(profile)

    ProviderImpl.oidc(Name, client, pkce = true)((tokens, _) => fetch(tokens))

  def profile(json: Json): Either[OAuthError, UserProfile] =
    ProviderImpl.required(json, "sub").map { id =>
      UserProfile(
        provider = Name,
        id = id,
        name = json.string("name"),
        username = None,
        email = json.string("email"),
        emailVerified = None,
        picture = json.string("picture"),
        raw = json,
      )
    }

  private def issuedBy(tenant: String)(claims: JwtClaims): Either[JwtError, Unit] =
    claims.string("tid") match
      case None                                                                                                 => Left(JwtError.MissingClaim("tid"))
      case Some(tenantId) if !claims.issuer.contains(issuer(tenantId).value)                                    => Left(JwtError.IssuerMismatch(Set(issuer(tenantId).value), claims.issuer))
      case Some(tenantId) if !MultiTenant(tenant) && tenant.contains('-') && !tenant.equalsIgnoreCase(tenantId) =>
        Left(JwtError.InvalidClaim("tid", s"the token belongs to tenant '$tenantId', not to '$tenant'"))
      case _                                                                                                    => Right(())
