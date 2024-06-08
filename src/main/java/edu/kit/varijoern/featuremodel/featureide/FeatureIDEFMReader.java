package edu.kit.varijoern.featuremodel.featureide;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.CoreFactoryWorkspaceLoader;
import de.ovgu.featureide.fm.core.base.impl.DefaultFeatureModelFactory;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.FMFormatManager;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelFormat;
import edu.kit.varijoern.featuremodel.FeatureModelReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Reads a feature model from a FeatureIDE feature model file.
 */
public class FeatureIDEFMReader implements FeatureModelReader {
    public static final String NAME = "featureide";
    private static boolean featureIDEInitialized = false;
    private static final Logger LOGGER = LogManager.getLogger();

    private final @NotNull Path path;

    /**
     * Creates a new {@link FeatureIDEFMReader} that reads the feature model from the specified path.
     *
     * @param path the path to the feature model file. Must be absolute.
     */
    public FeatureIDEFMReader(@NotNull Path path) {
        if (!featureIDEInitialized) {
            FMFormatManager.getInstance().addExtension(new XmlFeatureModelFormat());
            FMFactoryManager.getInstance().addExtension(new DefaultFeatureModelFactory());
            FMFactoryManager.getInstance().setWorkspaceLoader(new CoreFactoryWorkspaceLoader());
            featureIDEInitialized = true;
        }
        this.path = path;
    }

    @Override
    public @NotNull IFeatureModel read(@NotNull Path tmpPath) {
        LOGGER.info("Reading feature model from {}", this.path);
        FMFormatManager.getInstance().addExtension(new XmlFeatureModelFormat());
        return FeatureModelManager.load(this.path);
    }
}
