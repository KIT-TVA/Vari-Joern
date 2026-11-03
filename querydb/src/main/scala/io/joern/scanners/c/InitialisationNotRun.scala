package io.joern.scanners.c

import io.joern.scanners.*
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.Operators
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.joern.console.*
import io.joern.macros.QueryMacros.*
import io.shiftleft.semanticcpg.language.*

object InitializationNotRun extends QueryBundle {

  implicit val resolver: ICallResolver = NoResolve

  @q
  def initializationNotRun(): Query =
    Query.make(
      name = "initialization-not-run",
      author = Crew.baechlehoche,
      title = "Initialization code not run",
      description = """
        |Not running initialization code may lead to unexpected behavior.
        |""".stripMargin,
      score = 8,
      withStrRep({ cpg =>
          for {
            v <- cpg.local.where(_.method.name("<global>")).l
            lval = cpg.assignment.where(_.target.isIdentifier.code("<global> .*").name(v.name)).l

            if global(cpg, v)
            if !staticWithoutDereference(cpg, v)
            if !lval.method.exists(called(_))
          } yield v
      }),
      tags = List(QueryTags.default),
      codeExamples = CodeExamples(
        List("""
               |
               |char *ptr_bad;
               |
               |void init_no_call() {
               |    ptr_bad = malloc(8);
               |}
               |
               |int main_2(int argc, char *argv[]) {
               |    strcpy(ptr_bad, argv[0]); // BAD: pointer is used before init() is called.
               |}
               |
               |""".stripMargin),
        List("""
               |
               |char *ptr_good;
               |
               |void init_call() {
               |    ptr_good = malloc(8);
               |}
               |
               |int main(int argc, char *argv[]) {
               |    init_call()
               |    strcpy(ptr_good, argv[0]); // GOOD: pointer is used after init() is called.
               |
               |    main_2(arc, argv);
               |}
               |
               |""".stripMargin)
      )
    )

  private def global(cpg: Cpg, v: Local) = {
    cpg.assignment.where(_.method.name("<global>")).target.isIdentifier.name(v.name).isEmpty &&
      !v.code.matches("struct .*|.*\\[\\d*\\]") &&
      cpg.assignment.where(_.method.nameNot("<global>")).target.isIdentifier.code("<global> .*").name(v.name).nonEmpty
  }

  private def called(f: Method) = {
    f.name == "main" ||
      List(f).repeat(_.callIn.method)(_.until(_.name("main"))).nonEmpty
  }

  private def staticWithoutDereference(cpg: Cpg, v: Local) = {
    v.isStatic.nonEmpty &&
      cpg.call.nameExact(Operators.indirectFieldAccess, Operators.indirection, Operators.indirectIndexAccess).argument(1).isIdentifier.code("<global> .*").name(v.name).isEmpty
  }
}
