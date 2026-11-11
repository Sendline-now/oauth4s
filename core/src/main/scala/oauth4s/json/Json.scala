package oauth4s.json

enum Json:
  case Null
  case Bool(value: Boolean)
  case Num(value: BigDecimal)
  case Str(value: String)
  case Arr(values: Vector[Json])
  case Obj(fields: Vector[(String, Json)])

  def field(name: String): Option[Json] =
    this match
      case Obj(fields) => fields.reverseIterator.collectFirst { case (key, value) if key == name => value }
      case _           => None

  def asString: Option[String] =
    this match
      case Str(value) => Some(value)
      case _          => None

  def asBoolean: Option[Boolean] =
    this match
      case Bool(value) => Some(value)
      case _           => None

  def asLong: Option[Long] =
    this match
      case Num(value) if value.isValidLong => Some(value.toLong)
      case _                               => None

  def asDecimal: Option[BigDecimal] =
    this match
      case Num(value) => Some(value)
      case _          => None

  def asArray: Option[Vector[Json]] =
    this match
      case Arr(values) => Some(values)
      case _           => None

  def asObject: Option[Vector[(String, Json)]] =
    this match
      case Obj(fields) => Some(fields)
      case _           => None

  def string(name: String): Option[String]                       = field(name).flatMap(_.asString)
  def boolean(name: String): Option[Boolean]                     = field(name).flatMap(_.asBoolean)
  def long(name: String): Option[Long]                           = field(name).flatMap(_.asLong)
  def decimal(name: String): Option[BigDecimal]                  = field(name).flatMap(_.asDecimal)
  def array(name: String): Option[Vector[Json]]                  = field(name).flatMap(_.asArray)
  def strings(name: String): Vector[String]                      = array(name).fold(Vector.empty)(_.flatMap(_.asString))
  def path(names: String*): Option[Json]                         = names.foldLeft(Option(this))((json, name) => json.flatMap(_.field(name)))
  def as[A](using decoder: JsonDecoder[A]): Either[JsonError, A] = decoder.decode(this)

  def render(using codec: JsonCodec): String = codec.render(this)

object Json:
  val empty: Json = Obj(Vector.empty)

  def obj(fields: (String, Json)*): Json = Obj(fields.toVector)
  def arr(values: Json*): Json           = Arr(values.toVector)
  def str(value: String): Json           = Str(value)
  def bool(value: Boolean): Json         = Bool(value)
  def num(value: Long): Json             = Num(BigDecimal(value))
  def num(value: BigDecimal): Json       = Num(value)

  def strings(values: Iterable[String]): Json = Arr(values.iterator.map(Str(_)).toVector)

  def parse(text: String)(using codec: JsonCodec): Either[JsonError, Json] = codec.parse(text)
