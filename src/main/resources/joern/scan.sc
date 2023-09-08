import io.joern.console.scan.ScannerStarters

case class FindingOutput(name: String, title: String, description: String, score: String, evidence: List[EvidenceOutput])

case class EvidenceOutput(filename: String, lineNumber: Option[Integer])

@main def exec(codePath: String, outFile: String) = {
  importCode(codePath)
  run.scan
  val output = cpg.finding.map(finding => {
    val name = finding.keyValuePairs.find(_.key == "name").head.value
    val title = finding.keyValuePairs.find(_.key == "title").head.value
    val description = finding.keyValuePairs.find(_.key == "description").head.value
    val score = finding.keyValuePairs.find(_.key == "score").head.value
    val evidence = finding.evidence.map(evidence => {
      val filename = evidence.file.name.head
      val lineNumber = evidence.location.lineNumber
      EvidenceOutput(filename, lineNumber)
    }).l
    FindingOutput(name, title, description, score, evidence)
  })
  output.toJson #> outFile
}