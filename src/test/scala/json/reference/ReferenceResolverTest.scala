package json.reference

import argonaut.Argonaut._
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

import scalaz._

class ReferenceResolverTest extends FlatSpec with GeneratorDrivenPropertyChecks with Matchers {

  def containLeft(contain: String) =
    new Matcher[\/[_, _]] {
      def apply(left: \/[_, _]) = {
        val r = left match {
          case -\/(e) => e.toString.contains(contain)
          case _ => false
        }
        MatchResult(r, s"$left does not contain '$contain'", s"$left contains '$contain'")
      }
    }

  def shouldResolve(from: String, to: String) = from.stripMargin.parse.flatMap(j => ReferenceResolver(j)) shouldBe to.stripMargin.parse

  def shouldFailResolve(from: String, containErr: String) = from.stripMargin.parse.flatMap(j => ReferenceResolver(j)) should containLeft(containErr)


  ReferenceResolver.getClass.toString should "not change json doc if no references" in {
    shouldResolve(
      """
        |{
        | "source": "sourcedata",
        | "target": "source"
        |}
      """,
      """
        |{
        | "source": "sourcedata",
        | "target": "source"
        |}
      """
    )
  }

  it should "resolve fragment references (http://json-schema.org/address#/dependencies/extended-address) from remove doc" in {
    shouldResolve(
      """
        |{
        | "$ref": "http://json-schema.org/address#/dependencies/extended-address"
        |}
      """,
      """
        |"street-address"
      """
    )
  }

  it should "resolve fragment references (#/source) from current doc" in {
    shouldResolve(
      """
        |{
        | "source": "sourcedata",
        | "$ref": "#/source"
        |}
      """,
      """
        |"sourcedata"
      """
    )
  }

  it should "resolve fragment references (#/source) from nested nodes doc" in {
    shouldResolve(
      """
        |{
        | "source": "sourcedata",
        | "target": {"$ref": "#/source"}
        |}
      """,
      """
        | {
        | "source": "sourcedata",
        | "target": "sourcedata"
        | }
      """
    )
  }

  it should "resolve fragment references (#/source) from remote doc" in {
    shouldResolve(
      """
        |{
        | "source": {"$ref": "http://json-schema.org/address#/type"},
        | "target": [{"$ref": "http://json-schema.org/address#/dependencies/extended-address"}, {"$ref": "http://json-schema.org/address#/required"}],
        | "target2": {"$ref": "#/target"}
        |}
      """,
      """
        | {
        | "source": "object",
        | "target": ["street-address", ["locality", "region", "country-name"]],
        | "target2": ["street-address", ["locality", "region", "country-name"]]
        | }
      """
    )
  }


  it should "fail to resolve unknown references" in {
    shouldFailResolve(
      """
        |{
        | "a": {"$ref": "#/b"}
        |}
      """,
      "reference #/b not found"
    )

    shouldFailResolve(
      """
        |{
        | "a": {"$ref": "http://google.com/somedoc"}
        |}
      """,
      "reference http://google.com/somedoc not found"
    )

  }

  it should "fail to resolve cyclic references" in {
    shouldFailResolve(
      """
        |{
        | "a": {"$ref": "#/b"},
        | "b": {"$ref": "#/c"},
        | "c": {"$ref": "#/a"}
        |}
      """,
      "cyclic reference"
    )
  }

}