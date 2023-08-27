# Vari-Joern
Vari-Joern is a tool for analyzing software that has a [FeatureIDE](https://featureide.github.io/) feature model.
Its goal is to find weaknesses by running [Joern](https://joern.io) on a subset of all valid feature
combinations. This subset is chosen by sampling algorithms based on the results of the analyses of previously chosen
feature combinations.

## Usage
Vari-Joern is configured entirely by a TOML configuration file which is specified as a command line argument.
The following keys are used at the top level:
- `iterations`
  - Specifies how many sampler-composer-analyzer cycles should be executed.
  - Optional: yes, default: `1`
- `featureModelPath`
  - Specifies the location of the feature model file.
    Relative paths are relative to the location of the configuration file.
  - Optional: no

Furthermore, the `sampler`, `composer` and `analyzer` sections are required.
Each section configures a component implementation to be used by specifying its name and other configuration options.
For example, the `fixed` sampler could be configured as follows:
```toml
[sampler]
name = "fixed"
features = ["MyAwesomeFeature", "AnotherFeature"]
```

### Samplers
Samplers return a set of feature combinations which are then used by a composer to configure a variant of the software.
The resulting code is analyzed by Joern.
Samplers may use the results of the analysis to optimize the set of feature combinations returned in the next iteration.

Currently, only one sampler is available:

#### The `fixed` sampler
This sampler always returns the specified set of features. It ignores the results of previous iterations.

Currently, one option is available:
- `features`
  - Specifies the set of features the sampler returns.
  - Optional: no

### Composers
Composers create a variant of the software by enabling a set of features which has been chosen by a sampler.

Currently, only one composer is available:

#### The `antenna` composer
The Antenna composer is a simple preprocessor for Java source files.

It takes only one option:
- `source`
  - Specifies the location of the original source code which has not been preprocessed yet.
    Relative paths are relative to the location of the configuration file.
  - Optional: no

### Analyzers
Analyzers are used to scan a composed software variant.

Only Joern is supported at the moment. Joern takes the following option:
- `command`
  - Specifies the name or the location of the `joern` executable.
  - Optional: yes, default: `"joern"`

### Example configuration
A complete configuration file might look like this:
```toml
feature-model = "model.xml"

[sampler]
name = "fixed"
features = ["MyAmazingFeature"]

[composer]
name = "antenna"
source = "src"

[analyzer]
name = "joern"
```