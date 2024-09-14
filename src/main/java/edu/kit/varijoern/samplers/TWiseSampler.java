package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.TWiseConfigurationGenerator;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * This sampler chooses a set of features that achieves t-wise coverage.
 */
public class TWiseSampler extends FeatureIDEGeneratorSampler {
    public static final String NAME = "t-wise";
    private static final Logger LOGGER = LogManager.getLogger();

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
        super(featureModel);
        this.t = t;
        this.maxSampleSize = maxSampleSize;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults)
            throws SamplerException {
        LOGGER.info("Calculating {}-wise sample", this.t);
        List<Map<String, Boolean>> result
                = calculateSample(cnf -> new TWiseConfigurationGenerator(cnf, this.t, this.maxSampleSize));
        LOGGER.info("Generated {} configurations", result.size());
        return result;
    }
}
