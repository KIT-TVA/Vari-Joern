package edu.kit.varijoern.featuremodel;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * An interface for classes that read feature models.
 */
public interface FeatureModelReader {
    /**
     * Reads a feature model. The path to the feature model is specified at the creation of the implementer.
     *
     * @param tmpPath a path to a directory used for temporary files that are needed for reading the feature model. Must
     *                be absolute.
     * @return the feature model
     * @throws IOException if an I/O error occurs
     */
    @NotNull
    IFeatureModel read(@NotNull Path tmpPath) throws IOException, FeatureModelReaderException;
}
