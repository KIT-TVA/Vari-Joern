package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.AggregatedAnalysisResult;
import edu.kit.varijoern.analyzers.FindingAggregation;
import edu.kit.varijoern.analyzers.ResultAggregator;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Aggregates the results of multiple Joern analysis runs.
 */
public class JoernResultAggregator extends ResultAggregator<JoernAnalysisResult, JoernFinding> {
    @Override
    protected Class<JoernAnalysisResult> getResultClass() {
        return JoernAnalysisResult.class;
    }

    @Override
    public @NotNull AggregatedAnalysisResult aggregateResults() {
        Map<?, List<Pair<JoernFinding, JoernAnalysisResult>>> groupedFindings;
        synchronized (this.results) {
            // Group findings by their evidence and query name, store the analysis result retrieve the enabled features
            // and the presence condition mappers later
            groupedFindings = this.results.stream()
                    .flatMap(result -> result.getFindings().stream()
                            .map(finding -> Pair.with(finding, result)))
                    .collect(Collectors.groupingBy(
                            findingPair -> Pair.with(
                                    findingPair.getValue0().getEvidence(),
                                    findingPair.getValue0().getName()
                            )
                    ));
        }
        return new AggregatedAnalysisResult(groupedFindings
                .values().stream()
                .map(findingPairs -> {
                    // Use the first finding as all findings in this group are (likely) equal.
                    JoernFinding firstFinding = findingPairs.get(0).getValue0();
                    return new FindingAggregation(firstFinding,
                            findingPairs.stream()
                                    .map(findingPair -> findingPair.getValue1().getConfiguration())
                                    .collect(Collectors.toSet()),
                            findingPairs.stream()
                                    .map(findingPair -> findingPair.getValue0().getCondition())
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet()),
                            firstFinding.getEvidence()
                    );
                })
                .collect(Collectors.toSet()));
    }
}
