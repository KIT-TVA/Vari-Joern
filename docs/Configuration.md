# The configuration file

The configuration file contains project-specific information about how Vari-Joern should analyze source code.
It is written in the [TOML](https://toml.io/) format.

Currently, there is one key used at the top level of the configuration file:

- `iterations`
    - Specifies how many sampler-composer-analyzer cycles should be executed.
    - Optional: yes, default: `1`

Furthermore, the `feature-model-reader`, `sampler`, `composer` and `analyzer` sections are required.
Each section configures a component implementation to be used by specifying its name and other configuration
implementation-specific options.
For example, the `fixed` sampler could be configured as follows:

```toml
[sampler]
name = "fixed"
features = [["MyAwesomeFeature"], ["MyAwesomeFeature", "AnotherFeature"]]
```

## Feature model readers

Feature model readers create a feature model by reading it from a single file or extracting it from a build system.

The following feature model readers are available:

- [FeatureIDE feature model reader](feature-model-readers/FeatureIDE.md): Reads FeatureIDE models in XML format.
- [Torte-kmax feature model reader](feature-model-readers/Torte-kmax.md): Extracts a feature model from Kconfig files.

## Samplers

Samplers return a set of feature combinations which are then used by a composer to configure a variant of the software.
The resulting code is analyzed by Joern.
Samplers may use the results of the analysis to optimize the set of feature combinations returned in the next iteration.

The following samplers are available:

- [Fixed sampler](samplers/Fixed.md): Always returns the same set of features.
- [T-Wise sampler](samplers/T-Wise.md): Returns a set of configurations that achieves t-wise coverage.

## Composers

Composers create a variant of the software by enabling a set of features which has been chosen by a sampler.

The following composers are available:

- [Antenna composer](composers/Antenna.md): A simple preprocessor for Java source files.
- [Kbuild composer](composers/Kbuild.md): A composer for Kbuild, the Linux kernel build system.

## Analyzers

Analyzers are used to scan a composed software variant. Only [Joern](analyzers/Joern.md) is supported at the moment.

## Example configuration

A complete configuration file might look like this:

```toml
[feature-model-reader]
name = "featureide"
path = "model.xml"

[sampler]
name = "fixed"
features = [["MyAmazingFeature"]]

[composer]
name = "antenna"
source = "src"

[analyzer]
name = "joern"
```
