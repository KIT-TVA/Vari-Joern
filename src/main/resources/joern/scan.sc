import io.joern.console.scan.ScannerStarters

@main def exec(codePath: String, outFile: String) = {
  importCode(codePath)
  run.scan
  cpg.finding.toJson #> outFile
}