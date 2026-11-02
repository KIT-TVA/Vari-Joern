package io.joern.scanners.c

import io.joern.scanners.*
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.Operators
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.joern.console.*
import io.joern.macros.QueryMacros.*
import io.shiftleft.semanticcpg.language.*

object NotInitialized extends QueryBundle {

  @q
  def notInitialized(): Query =
    Query.make(
      name = "not-initialized",
      author = Crew.baechlehoche,
      title = "Variable not initialized before use",
      description = """
        |Using an uninitialized variable may lead to undefined results.
        |""".stripMargin,
      score = 8,
      withStrRep({ cpg =>
        for {
          v <- cpg.local.where(_.method.nameNot("<global>")).l
          use <- uses(cpg, v).l
          defs = defines(cpg, v).l
          if undefinedUse(v)
          if !defs.exists(c => c.dominates.contains(use))
        } yield use
      }),
      tags = List(QueryTags.default),
      codeExamples = CodeExamples(
        List("""
               |
               |int uninitialized() {
               |    int i;
               |    int g = i * 2; // BAD: i is used before it is initialized.
               |}
               |
               |""".stripMargin),
        List("""
               |
               |int initialized() {
               |    int i = 0;
               |    int g = i * 2; // GOOD: i is used after it is initialized.
               |}
               |
               |""".stripMargin)
      )
    )

  private def uses(cpg: Cpg, lv: Local) = {
    cpg.identifier
      .filter { id =>
        !id.astParent
          .filter(_.isCall)
          .collect { case call: Call => call }
          .isAssignment
          .target
          .contains(id)
      }.filter(_.refsTo.contains(lv))
      .repeat(_.astParent)(_.until(_.isCall))
      .isCall
      .filter(c => c.name != Operators.sizeOf)
  }

  private def defines(cpg: Cpg, lv: Local) = {
    cpg.assignment.filter(_.target.collect { case id: Identifier => id }.refsTo.contains(lv)).isCall
  }

  private def isAggregateType(t: String) = { t.matches("struct .*|.*\\[\\d*\\]") }

  private def undefinedUse(v: Local) = {
    // it is hard to tell when a struct or array has been initialized, so we ignore them
    !isAggregateType(v.typeFullName) && v.isStatic.isEmpty && v.typeFullName != "va_list"
  }
}
