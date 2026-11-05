package io.joern.scanners.c

import io.joern.scanners.*
import io.joern.console.*
import io.joern.macros.QueryMacros.*
import io.shiftleft.semanticcpg.language.*
import io.joern.dataflowengineoss.language.*
import io.joern.dataflowengineoss.queryengine.EngineContext

object MemoryNeverFreed extends QueryBundle {

  @q
  def memoryNeverFreed()(implicit context: EngineContext): Query =
    Query.make(
      name = "memory-never-freed",
      author = Crew.tbaechle,
      title = "Memory is never freed",
      description = """
        |A function always returns before freeing memory that was allocated in the function.
        |Freeing all memory allocated in the function before returning ties the lifetime of the memory
        |blocks to that of the function call, making it easier to avoid and detect memory leaks.
        |""".stripMargin,
      score = 8,
      withStrRep({ cpg =>
        cpg.call
          .name("malloc|calloc|realloc")
          .filter { alloc =>
            cpg.call.name("free")
              .argument(1)
              .reachableBy(alloc)
              .isEmpty
          }
      }),
      tags = List(QueryTags.default),
      codeExamples = CodeExamples(
        List("""
               |
               |int buffer_not_freed() {
               |    int *buff = malloc(8);
               |    int status = 0;
               |    return status; //BAD: buff is never freed.
               |}
               |
               |""".stripMargin),
        List("""
               |
               |int buffer_freed() {
               |    int *buff = malloc(8);
               |    int status = 0;
               |    free(buff); // GOOD: buff is freed.
               |    return status;
               |}
               |
               |""".stripMargin)
      )
    )
}
