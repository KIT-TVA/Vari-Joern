package io.joern.scanners.c

import io.joern.scanners.*
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.Operators
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.joern.console.*
import io.joern.macros.QueryMacros.*
import io.shiftleft.semanticcpg.language.*

object GlobalUseBeforeInit extends QueryBundle {

  implicit val resolver: ICallResolver = NoResolve

  @q
  def globalUseBeforeInit(): Query =
    Query.make(
      name = "global-use-before-init",
      author = Crew.baechlehoche,
      title = "Global variable may be used before initialization",
      description = """
        |Using an uninitialized variable may lead to undefined results.
        |""".stripMargin,
      score = 8,
      withStrRep({ cpg =>
        for {
          v <- cpg.local.where(_.method.name("<global>")).l
          use <- useFunc(cpg, v).l
          init = aggregateCalls(initFunc(cpg, v)).l
          if !initialisedAtDeclaration(cpg, v)
          if !init.exists(c => c.dominates.contains(use))
        } yield use
      }),
      tags = List(QueryTags.default),
      codeExamples = CodeExamples(
        List("""
               |
               |int global;
               |
               |void initGlobals() {
               |    global = 0;
               |}
               |
               |int uninitialized_global() {
               |    printf("%d", global); // BAD: global used before it is initialized.
               |    initGlobals();
               |}
               |
               |""".stripMargin),
        List("""
               |
               |int global;
               |
               |void initGlobals() {
               |    global = 0;
               |}
               |
               |int initialized_global() {
               |    initGlobals(); // GOOD: global is initialized here.
               |    printf("%d", global);
               |}
               |
               |
               |""".stripMargin)
      )
    )

  private def dominatingInitInFunc(v: Local, f: Method, node: Call) = {
    f.call.isAssignment.where(_.target.isIdentifier.code("<global> .*").name(v.name)).dominates.contains(node)
  }

  private def safeAccess(access: Call) = {
    // it is safe if the variable access is part of a `sizeof` expression
    access.name == Operators.sizeOf
  }

  private def useFunc(cpg: Cpg, v: Local) = {
    cpg.identifier
      .filter { id =>
        !id.astParent
          .filter(_.isCall)
          .collect { case call: Call => call }
          .isAssignment
          .target
          .contains(id)
      }
      .code("<global> .*")
      .name(v.name)
      .repeat(_.astParent)(_.until(_.isCall))
      .isCall
      .filter(c => !safeAccess(c))
      .filter(c => !dominatingInitInFunc(v, c.method, c))
  }

  private def initFunc(cpg: Cpg, v: Local) = {
    cpg.assignment.where(_.method.nameNot("<global>")).where(_.target.isIdentifier.code("<global> .*").name(v.name)).isCall
  }

  private def aggregateCalls(f: Iterator[Call]) = {
    f.repeat(_.method.callIn)(_.emit(_.where(_.isCall)).until(_.method.name("main")))
  }

  private def initialisedAtDeclaration(cpg: Cpg, v: Local) = {
    cpg.assignment.where(_.method.name("<global>")).target.isIdentifier.name(v.name).nonEmpty
  }
}
