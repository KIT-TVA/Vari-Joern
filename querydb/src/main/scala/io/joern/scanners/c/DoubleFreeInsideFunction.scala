package io.joern.scanners.c

import io.joern.scanners.*
import io.joern.console.*
import io.joern.macros.QueryMacros.*
import io.shiftleft.semanticcpg.language.*
import io.joern.dataflowengineoss.language.*
import io.joern.dataflowengineoss.queryengine.EngineContext

object DoubleFreeInsideFunction extends QueryBundle {

  @q
  def doubleFreeInsideFunction()(implicit context: EngineContext): Query =
    Query.make(
      name = "double-free-in-function",
      author = Crew.tbaechle,
      title = "Potential double free inside function",
      description = """
        |Freeing a resource more than once inside a function can lead to undefined behavior and cause memory corruption.
        |""".stripMargin,
      score = 9,
      withStrRep({ cpg =>
        cpg.call.name("free").filter { secondFree =>
          val otherFreeCalls = secondFree.method.call.name("free")

          otherFreeCalls.exists { firstFree =>
            firstFree != secondFree &&
              firstFree.argument(1).code == secondFree.argument(1).code &&
              secondFree.argument(1).reachableBy(firstFree.argument(1)).nonEmpty
          }
        }
      }),
      tags = List(QueryTags.default),
      codeExamples = CodeExamples(
        List("""
               |
               |int* double_free() {
               |    int *buff = malloc(8);
               |    do_stuff(buff);
               |    free(buff);
               |
               |    int *new_buffer = malloc(8);
               |    free(buff); // BAD: If new_buffer is assigned the same address as buff,
               |                // the memory allocator will free the new buffer memory region,
               |                // leading to use-after-free problems and memory corruption.
               |    return new_buffer;
               |}
               |
               |""".stripMargin),
        List("""
               |
               |int* single_free() {
               |    int *buff = malloc(8);
               |    do_stuff(buff);
               |    free(buff); // GOOD: buff is only freed once.
               |
               |    int *new_buffer = malloc(8);
               |    return new_buffer;
               |}
               |
               |""".stripMargin)
      )
    )
}
