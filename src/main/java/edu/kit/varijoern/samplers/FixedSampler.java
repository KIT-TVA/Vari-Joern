package edu.kit.varijoern.samplers;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FixedSampler implements Sampler {
    public static final String NAME = "fixed";
    private final List<String> features;

    public FixedSampler(List<String> features) {
        this.features = List.copyOf(features);
    }

    @Override
    public @NotNull List<List<String>> sample() {
        return List.of(features);
    }
}
