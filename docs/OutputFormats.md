# Output formats

Depending on the analysis strategy, Vari-Joern supports multiple output formats: 

- **Product-Based Strategy**: `text` and `json`
- **Family-Based Strategy**: `json`

`text` is a human-readable format which is not further specified. `json` generates a JSON file with the structure 
described in the next section.

## JSON output format

### Product-Based Strategy

- `individualResults`: An array of objects, each representing the results of the analysis of a single variant.
  Each object has the following fields:
    - `enabledFeatures`: An object mapping feature names to a boolean indicating whether the feature is enabled in the
      analyzed combination.
    - `findings`: An array of [Finding objects](#Finding) representing the findings in the analyzed variant.
- `aggregatedResult`: Analyzing all variants yields a list of findings for each variant. Since a single finding can
  affect multiple variants, the findings are grouped to identify equal findings with each other across variants. This
  object contains these aggregated results and has the following field:
    - `findingAggregations`: An array of objects, each representing the aggregation of a single finding across all
      analyzed variants. Each object has the following fields:
        - `finding`: A [Finding object](#Finding) representing the finding.
        - `affectedAnalyzedVariants`: An array of objects representing the variants in which the finding was found.
          Each object is a mapping from feature names to a boolean indicating whether the feature is enabled in the
          variant.
        - `evidence`: An array of [SourceLocation](#SourceLocation) objects representing the evidence supporting the
          finding.
        - `possibleConditions`: An array of strings representing the determined presence conditions of the finding. Most
          of the time, this array will have at most one element, but it may contain more than one because, due to
          inaccuracies, the determined presence condition depends on the variant in which it was determined.
          See [NodeDeserializer.java](../src/main/java/edu/kit/varijoern/serialization/NodeDeserializer.java) for the syntax of the presence condition.

#### Finding

A finding is an object with the following fields:

- `name`: The name of the kind of this finding.
- `evidence`: An array of [SourceLocation](#SourceLocation) objects representing the evidence supporting the finding.
- `condition`: A string representing the presence condition of the finding. See
  [NodeDeserializer.java](../src/main/java/edu/kit/varijoern/serialization/NodeDeserializer.java) for the syntax of the
  presence condition. May be null if the presence condition is unknown.
- `title` (specific to the Joern analyzer): A short string describing the finding.
- `description` (specific to the Joern analyzer): A longer description of the finding.
- `score` (specific to the Joern analyzer): A number indicating the severity of the finding.

#### SourceLocation

A source location is an object with the following fields:

- `file`: The path to the file containing the source code. Usually, this is path is relative to the root of the analyzed
  project that was passed to the composer.
- `line`: The line number in the file where the source code is located.



### Family-Based Strategy

- `id`: Unique identifier of the warning in the report.
- `input_file`: The desugared source file on which the warning was raised.
- `input_line`: The line number within the desugared source file at which the warning was raised
- `ther_input_lines`: A list of other line numbers at which the same warning was raised (i.e., duplicates).
- `original_file`: The unpreprocessed source file associated with `input_file`.
- `original_line`: The lange range in the unpreprocessed source file at which the warning should be found.
- `function_line_range`: The name of the surrounding function (or GLOBAL in case of global scope) and the associated 
  line range (e.g., static void  (__strip_2340) (char  * (__str_2339)) :40:54).
- `message`: The message of the matching Joern query.
- `sanitized_message`: Identical to `message` as Joern's message does not need to be sanitized from desugaring-renamings.
- `presence_condition`: The presence condition under which the warning manifests.
- `feasible`: Whether the presence condition is SAT.
- `configuration`: An exemplary configuration that does satisfy the presence condition.  
- `analysis_time`: The time required for analyzing the source file in which the warning was reported with Joern.
- `desugaring_time`: The time required for desugaring the source file in which the warning was reported.
- `get_recommended_space`: Always `null` (legacy field of the original Sugarlyzer framework).
- `remove_errors`: Always `null` (legacy field of the original Sugarlyzer framework).
- `verified`: Always `null` (legacy field of the original Sugarlyzer framework).
- `name`: The name of the matching Joern query.
- `description`: The description of the matching Joern query. 
- `score`: The score of the matching Joern query.
