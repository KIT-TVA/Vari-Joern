# The torte-kmax feature model reader

This reader extracts a feature model from a Kconfig files. It uses
[torte](https://github.com/ekuiter/torte/tree/79a4df3) and its kmax integration. One challenge of Kconfig is that each
codebase uses its own implementation. Therefore, it is necessary to specify which implementation of Kconfig is used.
A limitation of Vari-Joern is that it only supports boolean options in feature models, while Kconfig has more types.
For this reason, tristate options are treated as boolean options and some options, for example numerical option, are not
supported and removed from the feature model. See the
[torte documentation](https://github.com/ekuiter/torte/tree/79a4df3?tab=readme-ov-file#extraction-transformation-and-analysis)
for more information about torte's limitations.

## Requirements

Torte is downloaded automatically, but it depends on Docker being installed.

## Configuration

This reader takes the following options:

- `path`
    - Specifies the source directory.
      Relative paths are relative to the location of the configuration file.
    - Optional: no
- `system`
    - Specifies the used Kconfig implementation. Currently, `busybox` and `linux` are supported.
    - Optional: no

For example, the reader could be configured as follows:

```toml
[feature-model-reader]
name = "torte-kmax"
path = "path/to/linux-source"
system = "linux"
```
