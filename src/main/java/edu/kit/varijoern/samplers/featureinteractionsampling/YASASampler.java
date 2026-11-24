package edu.kit.varijoern.samplers.featureinteractionsampling;

import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.TWiseConfigurationGenerator;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.samplers.FeatureIDEGeneratorSampler;
import edu.kit.varijoern.samplers.SamplerException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * This sampler chooses a set of features that achieves t-wise feature interaction coverage using YASA.
 */
public class YASASampler extends FeatureIDEGeneratorSampler {
    public static final String NAME = "yasa";
    private static final Logger LOGGER = LogManager.getLogger();

    private final int t;
    private final int maxSampleSize;
    private final int iterations;

    /**
     * Creates a new {@link YASASampler} which generates samples for the specified feature model (expressed as
     * {@link IFeatureModel}).
     *
     * @param featureModel  the feature model (expressed as {@link IFeatureModel}).
     * @param t             the t-wise feature interaction coverage that should be achieved.
     * @param maxSampleSize the maximum number of configurations to be generated.
     * @param iterations the number how often the process of removing and resampling shall be repeated to improve the
     *                   overall result.
     */
    public YASASampler(@NotNull IFeatureModel featureModel, int t, int maxSampleSize, int iterations) {
        super(featureModel);
        this.t = t;
        this.maxSampleSize = maxSampleSize;
        this.iterations = iterations;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults,
                                                      @NotNull Path tmpPath) throws SamplerException {
        LOGGER.info("Calculating {}-wise sample using YASA.", this.t);
        List<Map<String, Boolean>> result = calculateSample(cnf -> {
                    TWiseConfigurationGenerator generator = new TWiseConfigurationGenerator(cnf, this.t,
                            this.maxSampleSize);
                    generator.setIterations(this.iterations);
                    generator.setRandom(new Random()); // By default, the generator uses a fixed seed.
                    return generator;
                }
        );
        LOGGER.info("Sampled {} configurations using YASA.", result.size());
        return result;
    }
}
