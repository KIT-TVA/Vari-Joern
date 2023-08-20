package edu.kit.varijoern.samplers;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Sampler {
    /**
     * Returns a sample of feature combinations.
     *
     * @return a list of feature combinations
     */
    @NotNull List<List<String>> sample();
}
