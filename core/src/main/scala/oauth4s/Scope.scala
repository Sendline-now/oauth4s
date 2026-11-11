package oauth4s

import scala.annotation.targetName

opaque type Scope = Vector[String]

object Scope:
  val empty: Scope = Vector.empty

  val Separator: String = " "

  def of(tokens: String*): Scope = tokens.iterator.map(_.trim).filter(_.nonEmpty).toVector.distinct

  def parse(raw: String): Scope = of(raw.split("[\\s,]+")*)

  extension (scope: Scope)
    inline def values: Vector[String]     = scope
    def isEmpty: Boolean                  = scope.isEmpty
    def nonEmpty: Boolean                 = scope.nonEmpty
    def contains(token: String): Boolean  = scope.contains(token)
    def render: String                    = scope.mkString(Separator)
    def render(separator: String): String = scope.mkString(separator)

    def orElse(other: Scope): Scope =
      if scope.isEmpty
      then other
      else scope

    @targetName("union")
    def ++(other: Scope): Scope = (scope ++ other).distinct
