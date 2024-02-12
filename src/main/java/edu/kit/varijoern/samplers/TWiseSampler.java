package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.FeatureModelCNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.Variables;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.TWiseConfigurationGenerator;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.job.monitor.ConsoleMonitor;
import edu.kit.varijoern.analyzers.AnalysisResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This sampler chooses a set of features that achieves t-wise coverage.
 */
public class TWiseSampler implements Sampler {
    public static final String NAME = "t-wise";
    private final IFeatureModel featureModel;
    private final int t;
    private final int maxSampleSize;

    /**
     * Creates a new {@link TWiseSampler} which generates sampler for the specified feature model.
     *
     * @param featureModel  the feature model
     * @param t             the parameter t
     * @param maxSampleSize the maximum number of combinations to be generated
     */
    public TWiseSampler(IFeatureModel featureModel, int t, int maxSampleSize) {
        this.featureModel = featureModel;
        this.t = t;
        this.maxSampleSize = maxSampleSize;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(List<AnalysisResult> analysisResults) throws SamplerException {
        CNF cnf = FeatureModelCNF.fromFeatureModel(this.featureModel);
        TWiseConfigurationGenerator generator = new TWiseConfigurationGenerator(cnf, this.t, this.maxSampleSize);
        List<LiteralSet> rawSample;
        try {
            rawSample = generator.analyze(new ConsoleMonitor<>());
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new SamplerException("TWiseConfigurationGenerator threw an exception", e);
        }

        Variables variables = cnf.getVariables();
        List<List<String>> enabledFeatures = rawSample.stream().map(variables::convertToString).toList();
        List<Map<String, Boolean>> result = new ArrayList<>();
        for (List<String> featureCombination : enabledFeatures) {
            Map<String, Boolean> assignment = this.featureModel.getFeatures().stream()
                .collect(Collectors.toMap(IFeature::getName, feature -> false));
            for (String feature : featureCombination) {
                if (assignment.put(feature, true) == null) {
                    throw new SamplerException("Feature %s does not exist in the feature model".formatted(feature));
                }
            }
            result.add(assignment);
        }
        return result;
    }
}
