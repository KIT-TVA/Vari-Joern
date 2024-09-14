package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.FeatureModelCNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.Variables;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.AConfigurationGenerator;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class FeatureIDEGeneratorSampler implements Sampler {
    protected final @NotNull IFeatureModel featureModel;

    protected FeatureIDEGeneratorSampler(@NotNull IFeatureModel featureModel) {
        this.featureModel = featureModel;
    }

    protected @NotNull List<Map<String, Boolean>> calculateSample(
            @NotNull Function<CNF, AConfigurationGenerator> generatorCreator)
            throws SamplerException {
        CNF cnf = FeatureModelCNF.fromFeatureModel(this.featureModel);
        List<LiteralSet> rawSample;
        // CHECKSTYLE:OFF: IllegalCatch
        // We would like to wrap all exceptions in a SamplerException, except for RuntimeExceptions.
        // Since TWiseConfigurationGenerator#analyze declares to throw Exception, we have to catch Exception.
        try {
            rawSample = generatorCreator.apply(cnf).analyze(new NullMonitor<>());
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
        return result;
    }
}
