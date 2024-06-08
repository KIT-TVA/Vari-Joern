package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.FeatureModelCNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.Variables;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.TWiseConfigurationGenerator;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;
import edu.kit.varijoern.analyzers.AnalysisResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This sampler chooses a set of features that achieves t-wise coverage.
 */
public class TWiseSampler implements Sampler {
    public static final String NAME = "t-wise";
    private static final Logger LOGGER = LogManager.getLogger();

    private final @NotNull IFeatureModel featureModel;
    private final int t;
    private final int maxSampleSize;

    /**
     * Creates a new {@link TWiseSampler} which generates samples for the specified feature model.
     *
     * @param featureModel  the feature model
     * @param t             the parameter t
     * @param maxSampleSize the maximum number of configurations to be generated
     */
    public TWiseSampler(@NotNull IFeatureModel featureModel, int t, int maxSampleSize) {
        this.featureModel = featureModel;
        this.t = t;
        this.maxSampleSize = maxSampleSize;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult> analysisResults)
            throws SamplerException {
        LOGGER.info("Calculating {}-wise sample", this.t);
        CNF cnf = FeatureModelCNF.fromFeatureModel(this.featureModel);
        TWiseConfigurationGenerator generator = new TWiseConfigurationGenerator(cnf, this.t, this.maxSampleSize);
        List<LiteralSet> rawSample;
        // CHECKSTYLE:OFF: IllegalCatch
        // We would like to wrap all exceptions in a SamplerException, except for RuntimeExceptions.
        // Since TWiseConfigurationGenerator#analyze declares to throw Exception, we have to catch Exception.
        try {
            rawSample = generator.analyze(new NullMonitor<>());
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new SamplerException("TWiseConfigurationGenerator threw an exception", e);
        }
        // CHECKSTYLE:ON: IllegalCatch

        Variables variables = cnf.getVariables();
        List<List<String>> enabledFeatures = rawSample.stream().map(variables::convertToString).toList();
        List<Map<String, Boolean>> result = new ArrayList<>();
        for (List<String> configuration : enabledFeatures) {
            Map<String, Boolean> assignment = this.featureModel.getFeatures().stream()
                    .collect(Collectors.toMap(IFeature::getName, feature -> false));
            for (String feature : configuration) {
                if (assignment.put(feature, true) == null) {
                    throw new SamplerException("Feature %s does not exist in the feature model".formatted(feature));
                }
            }
            result.add(assignment);
        }
        LOGGER.info("Generated {} configurations", result.size());
        return result;
    }
}
