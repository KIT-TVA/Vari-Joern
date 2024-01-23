package edu.kit.varijoern.featuremodel;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.CoreFactoryWorkspaceLoader;
import de.ovgu.featureide.fm.core.base.impl.DefaultFeatureModelFactory;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.FMFormatManager;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelFormat;

import java.nio.file.Path;

/**
 * Reads a feature model from a FeatureIDE feature model file.
 */
public class FeatureIDEFMReader implements FeatureModelReader {
    public static final String NAME = "featureide-fm-reader";
    private static boolean featureIDEInitialized = false;
    private final Path path;

    /**
     * Creates a new {@link FeatureIDEFMReader} that reads the feature model from the specified path.
     *
     * @param path the path to the feature model file
     */
    public FeatureIDEFMReader(Path path) {
        if (!featureIDEInitialized) {
            FMFormatManager.getInstance().addExtension(new XmlFeatureModelFormat());
            FMFactoryManager.getInstance().addExtension(new DefaultFeatureModelFactory());
            FMFactoryManager.getInstance().setWorkspaceLoader(new CoreFactoryWorkspaceLoader());
            featureIDEInitialized = true;
        }
        this.path = path;
    }

    @Override
    public IFeatureModel read(Path tmpPath) {
        FMFormatManager.getInstance().addExtension(new XmlFeatureModelFormat());
        return FeatureModelManager.load(this.path);
    }
}
