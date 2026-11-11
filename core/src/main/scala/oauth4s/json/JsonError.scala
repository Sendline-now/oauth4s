package oauth4s.json

enum JsonError:
  case Malformed(reason: String, offset: Int)
  case MissingField(name: String)
  case InvalidField(name: String, reason: String)
  case Custom(reason: String)

  def message: String =
    this match
      case Malformed(reason, offset)  => s"$reason at offset $offset"
      case MissingField(name)         => s"'$name' is missing"
      case InvalidField(name, reason) => s"'$name' $reason"
      case Custom(reason)             => reason
