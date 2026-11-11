package oauth4s.it

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters.given

final class DocsItSpec extends AnyWordSpec, Matchers:
  private val Artifact = """"now\.sendline" %% "(oauth4s-[a-z0-9-]+)" % "([^"]+)"""".r

  private val examples: List[String] =
    val stream = Files.walk(Paths.get(sys.props("oauth4s.examples")))
    try stream.iterator.asScala.filter(_.toString.endsWith(".scala")).map(file => squeezed(Files.readString(file))).toList
    finally stream.close()

  private def squeezed(text: String): String = text.filterNot(_.isWhitespace)

  private def document(name: String): String = Files.readString(Paths.get(sys.props("oauth4s.root"), name), StandardCharsets.UTF_8)

  private def blocksOf(text: String): List[List[String]] =
    text.linesIterator
      .foldLeft((List.empty[List[String]], Option.empty[List[String]])) {
        case ((done, None), line) if line.trim == "```scala"   => (done, Some(Nil))
        case ((done, Some(block)), line) if line.trim == "```" => (done :+ block, None)
        case ((done, Some(block)), line)                       => (done, Some(block :+ line))
        case (acc, _)                                          => acc
      }
      ._1

  private def uncompiled(name: String): List[String] =
    blocksOf(document(name))
      .filterNot(_.exists(_.contains("libraryDependencies")))
      .filterNot(block => examples.exists(_.contains(squeezed(block.mkString("\n")))))
      .map(_.find(_.trim.nonEmpty).getOrElse(""))

  "the README" should {
    "show only Scala that compiles, every block copied from an example under it/src/test/scala/oauth4s/it/docs" in {
      (blocksOf(document("README.md")).size > 30, uncompiled("README.md")) shouldBe (true, Nil)
    }

    "ask for the artifacts of the version the build publishes" in {
      val asked = Artifact.findAllMatchIn(document("README.md")).map(found => found.group(1) -> found.group(2)).toList

      (asked.nonEmpty, asked.filterNot(_._2 == sys.props("oauth4s.version"))) shouldBe (true, Nil)
    }
  }

  "the recipes" should {
    "show only Scala that compiles, every block copied from an example under it/src/test/scala/oauth4s/it/docs" in {
      (blocksOf(document("RECIPES.md")).size, uncompiled("RECIPES.md")) shouldBe (7, Nil)
    }
  }
