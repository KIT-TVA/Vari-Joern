package io.joern.scanners.c

import io.joern.scanners.*
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.joern.console.*
import io.joern.macros.QueryMacros.*
import io.shiftleft.semanticcpg.language.*

object DeadCodeGoto extends QueryBundle {

  @q
  def deadCodeGoto(): Query =
    Query.make(
      name = "dead-code-goto",
      author = Crew.baechlehoche,
      title = "Dead code due to goto or break statement",
      description = """
        |A goto or break statement is followed by unreachable code.
        |""".stripMargin,
      score = 7,
      withStrRep({ cpg =>
        for {
          js <- (cpg.break.l ++ cpg.continue.l ++ cpg.goto.l)
          b = js.astParent
          i = js.order
          s = b.astChildren.order(i + 1).l
          // the next statement exists
          if s.nonEmpty
          // the next statement isn't jumped to
          if s.collect { case jt: JumpTarget => jt }.isEmpty
          // the next statement isn't breaking out of a switch
          if s.inAstMinusLeaf.collect { case cs: ControlStructure => cs }.isSwitch.isEmpty
          // the next statement isn't a loop that can be jumped into
          if s.collect { case cs: ControlStructure => cs }.controlStructureType("WHILE|FOR|DO").ast.collect { case jt: JumpTarget => jt }.isEmpty
        } yield js
      }),
      tags = List(QueryTags.default),
      codeExamples = CodeExamples(
        List("""
               |
               |int unreachable() {
               |    void *ptr = malloc(8);
               |    goto err1;
               |    free(ptr); // BAD: this line is unreachable.
               |    err1: return -1;
               |}
               |
               |
               |""".stripMargin),
        List("""
               |
               |int reachable() {
               |    void *ptr = malloc(8);
               |    free(ptr); // GOOD: this line is reachable.
               |    goto err2;
               |    err2: return -1;
               |}
               |
               |""".stripMargin)
      )
    )
}
