package oauth4s.oidc

import oauth4s.OAuthError
import oauth4s.json.{Json, JsonDecoder, JsonError}

opaque type UserInfo = Json

object UserInfo:
  def apply(claims: Json): UserInfo = claims

  def fromJson(json: Json): Either[OAuthError, UserInfo] =
    json.string("sub").filter(_.nonEmpty).toRight(OAuthError.MissingField("sub")).map(_ => json)

  extension (info: UserInfo)
    inline def raw: Json                                               = info
    def claim(name: String): Option[Json]                              = info.field(name)
    def subject: Option[String]                                        = info.string("sub")
    def name: Option[String]                                           = info.string("name")
    def givenName: Option[String]                                      = info.string("given_name")
    def familyName: Option[String]                                     = info.string("family_name")
    def preferredUsername: Option[String]                              = info.string("preferred_username")
    def email: Option[String]                                          = info.string("email")
    def emailVerified: Option[Boolean]                                 = info.boolean("email_verified").orElse(info.string("email_verified").flatMap(_.toBooleanOption))
    def picture: Option[String]                                        = info.string("picture")
    def locale: Option[String]                                         = info.string("locale")
    def decode[A](using decoder: JsonDecoder[A]): Either[JsonError, A] = decoder.decode(info)
