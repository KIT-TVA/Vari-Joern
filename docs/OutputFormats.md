# Output formats

Vari-Joern supports two output formats: `text` and `json`. `text` is a human-readable format which is not further
specified. `json` generates a JSON object with the structure described in the next section.

## JSON output format

- `individualResults`: An array of objects, each representing the results of the analysis of a single variant.
  Each object has the following fields:
  - `enabledFeatures`: An object mapping feature names to a boolean indicating whether the feature is enabled in the
    analyzed combination.
  - `findings`: An array of [Findings](#Finding), each representing a finding in the analyzed variant.
- `aggregatedResult`: Analyzing all variants yields a list of findings for each variant. Since a single finding can
  affect multiple variants, the findings are grouped to identify equal findings with each other across variants. This
  object contains these aggregated results and has the following field:
  - `findingAggregations`: An array of objects, each representing the aggregation of a single finding across all
    analyzed variants. Each object has the following fields:
    - `finding`: A [Finding object](#Finding) representing the finding.
    - `affectedAnalyzedVariants`: An array of objects representing the variants in which the finding was found.
       Each object is a mapping from feature names to a boolean indicating whether the feature is enabled in the
       variant.
    - `possibleConditions`: An array of strings representing the determined presence conditions of the finding. Most of
      the time, this array will have at most one element, but it may contain more than one because, due to inaccuracies,
      the determined presence condition depends on the variant in which it was determined.

### Finding
A finding is an object with the following fields:
- `title`: A short string describing the finding.
- `evidence`: An array of [Evidence objects](#Evidence), each representing a piece of evidence supporting the finding.
- `name` (specific to the Joern analyzer): The name of the finding as used in Joern.
- `description` (specific to the Joern analyzer): A longer description of the finding.
- `score` (specific to the Joern analyzer): A number indicating the severity of the finding.
