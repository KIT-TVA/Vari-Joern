package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.UniformRandomConfigurationGenerator;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * This sampler chooses a sample of configurations uniformly at random.
 */
public class UniformSampler extends FeatureIDEGeneratorSampler {
    public static final String NAME = "uniform";
    private static final Logger LOGGER = LogManager.getLogger();

    private final int sampleSize;

    /**
     * Creates a new {@link UniformSampler} which generates samples for the specified feature model.
     *
     * @param featureModel the feature model
     * @param sampleSize   the number of configurations to be generated
     */
    public UniformSampler(@NotNull IFeatureModel featureModel, int sampleSize) {
        super(featureModel);
        this.sampleSize = sampleSize;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults)
            throws SamplerException {
        LOGGER.info("Calculating uniform sample");
        List<Map<String, Boolean>> result
                = calculateSample(cnf -> new UniformRandomConfigurationGenerator(cnf, this.sampleSize));
        LOGGER.info("Generated {} configurations", result.size());
        return result;
    }
}
