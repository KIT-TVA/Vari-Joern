import scala.language.implicitConversions

case class FindingOutput(name: String, title: String, description: String, score: String, evidence: List[EvidenceOutput])

case class EvidenceOutput(filename: String, lineNumber: Option[Integer])

@main def exec(cpgPath: String, outFile: String) = {
  importCpg(cpgPath)
  run.scan
  val output = cpg.finding.map(finding => {
    val name = finding.keyValuePairs.find(_.key == "name").head.value
    val title = finding.keyValuePairs.find(_.key == "title").head.value
    val description = finding.keyValuePairs.find(_.key == "description").head.value
    val score = finding.keyValuePairs.find(_.key == "score").head.value
    val evidence = finding.evidence.map(evidence => {
      val filename = evidence.file.name.head
      val lineNumber = evidence.location.lineNumber
      EvidenceOutput(filename, lineNumber.map(_.asInstanceOf[Integer]))
    }).l
    FindingOutput(name, title, description, score, evidence)
  })
  output.toJsonPretty #> outFile
}