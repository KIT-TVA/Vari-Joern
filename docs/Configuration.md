# The configuration file

The configuration file contains project-specific information about how Vari-Joern should analyze source code.
It is written in the [TOML](https://toml.io/) format.

Currently, there is one key used at the top level of the configuration file:

- `iterations`
    - Specifies how many sampler-composer-analyzer cycles should be executed.
    - Is only relevant for the product-based strategy.
    - Optional: yes, default: `1`

Furthermore, the `subject` section is required.
This section contains the `name` and `source_root` path to the system that should be analyzed.
An example is shown below.

```toml
[subject]
name = "busybox"
# Either absolute or relative to the config file.
source_root = "path/to/busybox"
```
Depending on the chosen analysis strategy (product-based vs. family-based) additional `product` and `family` tables need
to be populated as described below:



## Product-Based Strategy.

Within the product table, the `feature-model-reader`, `sampler`, `composer` and `analyzer` sections are required.
Each section configures a component implementation to be used by specifying its name and other configuration
implementation-specific options.
For example, the `fixed` sampler could be configured as follows:

```toml
[sampler]
name = "fixed"
features = [["MyAwesomeFeature"], ["MyAwesomeFeature", "AnotherFeature"]]
```

### Feature model readers

Feature model readers create a feature model by reading it from a single file or extracting it from a build system.

The following feature model readers are available:

- [FeatureIDE feature model reader](feature-model-readers/FeatureIDE.md): Reads FeatureIDE models in XML format.
- [Torte-kmax feature model reader](feature-model-readers/Torte-kmax.md): Extracts a feature model from Kconfig files.

### Samplers

Samplers return a set of feature combinations which are then used by a composer to configure a variant of the software.
The resulting code is analyzed by Joern.
Samplers may use the results of the analysis to optimize the set of feature combinations returned in the next iteration.

The following samplers are available:

- [Fixed sampler](samplers/Fixed.md): Always returns the same set of features.
- [T-Wise sampler](samplers/T-Wise.md): Returns a set of configurations that achieves t-wise coverage.

### Composers

Composers create a variant of the software by enabling a set of features which has been chosen by a sampler.

The following composers are available:

- [Antenna composer](composers/Antenna.md): A simple preprocessor for Java source files.
- [Kconfig composer](composers/Kconfig.md): A composer for Kconfig, the Linux kernel configuration system.

### Analyzers

Analyzers are used to scan a composed software variant. Only [Joern](analyzers/Joern.md) is supported at the moment.



## Family-Based Strategy

The only section required in the `family` table is the ``sugarlyzer`` section that configures the operation of Sugarlyzer.
This section has three fields:
- `analyzer_name`: The name of the SAST tool that should be used.
  - Currently, only `joern` is supported 
  - Mandatory
- `keep_intermediary_files`: Specifies, whether Sugarlyzer should omit cleaning intermediary files after the analysis has
  finished.
  - Optional, `false` by default
- `relative_paths`: Should absolute or relative paths be used in the resulting report file.
  - Optional, `false` by default


## Example configuration

A complete configuration file that can be used for both analysis strategies might look like this:

```toml
# Optional.
iterations = 2

# Mandatory table.
[subject]
name = "busybox"
# Either absolute or relative to the config file.
source_root = "path/to/busybox"

####################################################
# Only checked if product-based strategy is chosen.
####################################################
[product]

# Mandatory table for product-based strategy.
[product.analyzer]
name = "joern"

# Mandatory table for product-based strategy.
[feature-model-reader]
name = "featureide"
# Either absolute or relative to subject.source_root.
path = "path/to/feature-model.xml"

# Mandatory table for product-based strategy.
[product.sampler]
name = "fixed"
features = [["MyAwesomeFeature"], ["MyAwesomeFeature", "AnotherFeature"]]

# Mandatory table for product-based strategy.
[product.composer]
name = "kconfig"
# Either absolute or relative to subject.source_root.
source = "path/to/source-code"

####################################################
# Only checked if family-based strategy is chosen.
####################################################
[family]

# Mandatory table for family-based strategy.
[family.sugarlyzer]
analyzer_name = "joern"
# Optional configuration of Sugarlyzer.
keep_intermediary_files = true
relative_paths = true
```
