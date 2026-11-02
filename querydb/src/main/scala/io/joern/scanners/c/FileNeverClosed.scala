package io.joern.scanners.c

import io.joern.scanners.*
import io.joern.console.*
import io.joern.macros.QueryMacros.*
import io.shiftleft.semanticcpg.language.*
import io.shiftleft.codepropertygraph.generated.nodes.Call
import io.joern.dataflowengineoss.language.*
import io.joern.dataflowengineoss.queryengine.EngineContext

object FileNeverClosed extends QueryBundle {

  @q
  def fileNeverClosed()(implicit context: EngineContext): Query =
    Query.make(
      name = "file-never-closed",
      author = Crew.baechlehoche,
      title = "Open file is not closed",
      description = """
        |A function always returns before closing a file that was opened in the function.
        |Closing resources in the same function that opened them ties the lifetime of the resource
        |to that of the function call, making it easier to avoid and detect resource leaks.
        |""".stripMargin,
      score = 8,
      withStrRep({ cpg =>
        cpg.call.name("<operator>.assignment")
          .filter { assignment =>
            assignment.argument(2).isCall &&
            assignment.argument(2).asInstanceOf[Call].name == "fopen"
          }
          .filter { assignment =>
            val fp = assignment.argument(1)
            assignment.method.call.name("fclose")
              .argument(1)
              .reachableBy(Iterator(fp))
              .isEmpty
          }
      }),
      tags = List(QueryTags.default),
      codeExamples = CodeExamples(
        List("""
               |
               |int file_never_closed() {
               |    FILE *fp = fopen("foo.txt", "w");
               |    int status = 0;
               |    return status; // BAD: fp is never closed.
               |}
               |
               |""".stripMargin),
        List("""
               |
               |int file_closed() {
               |    FILE *fp = fopen("foo.txt", "w");
               |    int status = 0;
               |    fclose(fp); // GOOD: fp is closed.
               |    return status;
               |}
               |
               |""".stripMargin)
      )
    )
}
