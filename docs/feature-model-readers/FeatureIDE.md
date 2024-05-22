# The FeatureIDE Feature Model Reader

This feature model reader parses an existing [FeatureIDE](https://featureide.github.io/) feature model file in the XML
format. FeatureIDE is used by Vari-Joern as a library for managing feature models.

## Configuration

This reader is configured using the following option:

- `path`
    - Specifies the location of the FeatureIDE feature model file.
      Relative paths are relative to the location of the configuration file.
    - Optional: no

For example, the reader could be configured as follows:

```toml
[feature-model-reader]
name = "featureide-fm-reader"
path = "path/to/feature-model.xml"
```
