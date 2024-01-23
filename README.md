# Vari-Joern

Vari-Joern is a tool for analyzing software product lines.
Its goal is to find weaknesses by running [Joern](https://joern.io) on a subset of all valid feature
combinations. This subset is chosen by sampling algorithms based on the results of the analyses of previously chosen
feature combinations.

## Usage

Vari-Joern is configured entirely by a TOML configuration file which is specified as a command line argument.
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
features = ["MyAwesomeFeature", "AnotherFeature"]
```

### Feature model readers

Feature model readers create a feature model by reading it from a single file or extracting it from a build system.

The following feature model readers are available:

#### The `featureide-fm-reader` feature model reader

This is the simplest available reader. It parses an existing FeatureIDE feature model file.
It is configured using one option:

- `path`
    - Specifies the location of the FeatureIDE feature model file.
      Relative paths are relative to the location of the configuration file.
    - Optional: no

#### The `torte-kmax` feature model reader

This reader extracts a feature model from a Kconfig files. It uses
[torte](https://github.com/ekuiter/torte/tree/79a4df3) which is downloaded automatically but depends on Docker being
installed and rootless mode being enabled.

Tristate options are treated as boolean options. All options that are neither tristate nor boolean are ignored.

The reader takes the following options:

- `path`
    - Specifies the source directory.
      Relative paths are relative to the location of the configuration file.
    - Optional: no
- `system`
    - Specifies the used Kconfig implementation. Currently, `busybox` and `linux` are supported.

### Samplers

Samplers return a set of feature combinations which are then used by a composer to configure a variant of the software.
The resulting code is analyzed by Joern.
Samplers may use the results of the analysis to optimize the set of feature combinations returned in the next iteration.

The following samplers are available:

#### The `fixed` sampler

This sampler always returns the specified set of features. It ignores the results of previous iterations. If the
specified configuration contradicts the constraints of the feature model, the sampler will fail.

Currently, one option is available:

- `features`
    - Specifies the set of features the sampler returns.
    - Optional: no

#### The `t-wise` sampler

This sampler returns a sample that achieves t-wise feature coverage.
It takes one option:

- `t`
    - The parameter `t`. The sampler will try to cover all possible combinations of `t` features.
      Should be less than or equal to the number of total features.
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

#### The `kbuild` composer

The Kbuild composer generates variants for codebases that use the Kbuild build system. For more details on its
limitations and how it works, see the
[JavaDoc documentation](./src/main/java/edu/kit/varijoern/composers/kbuild/KbuildComposer.java) of the implementation.

To use this composer, [kmax](https://github.com/paulgazz/kmax) needs to be installed and in the path variable.
Vari-Joern has been tested with kmax version 4.5.2.

The composer takes the following options:

- `source`
    - Specifies the location of the source code.
      Relative paths are relative to the location of the configuration file.
    - Optional: no
- `system`
    - Specifies the used Kconfig/Kbuild implementation. Currently, `busybox` and `linux` are supported.
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
[feature-model-reader]
path = "model.xml"

[sampler]
name = "fixed"
features = ["MyAmazingFeature"]

[composer]
name = "antenna"
source = "src"

[analyzer]
name = "joern"
```