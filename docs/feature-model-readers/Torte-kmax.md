# The Torte-kmax Feature Model Reader

This reader extracts a feature model from a Kconfig files. It uses
[torte](https://github.com/ekuiter/torte/tree/79a4df3) and its kmax integration. One challenge of Kconfig is that each
codebase uses its own implementation. The feature model reader knows which implementation to assume by reading the
`name` field of the subject configuration. It supports the Kconfig implementation of the Linux kernel
(`name = "linux"`, version 6.7 works, but broke in some later version), BusyBox (`name = "busybox"`),
Fiasco (`name = "fiasco"`), and axTLS (`name = "axtls"`).

A limitation of Vari-Joern is that it only supports boolean options in feature models, while Kconfig has more types.
For this reason, tristate options are treated as boolean options and some options, for example numerical option, are not
supported and removed from the feature model. See the
[torte documentation](https://github.com/ekuiter/torte/tree/79a4df3?tab=readme-ov-file#extraction-transformation-and-analysis)
for more information about torte's limitations.

## Requirements

Torte is downloaded automatically, but it depends on Docker being installed.

## Configuration

This reader takes the following option:

- `path`
    - Specifies the source directory from which torte will extract the feature model from. In all supported subject
      systems, this is the source root of the subject.
      Relative paths are relative to the source root of the subject.
    - Optional: yes
    - Default: `.`

For example, the reader could be configured as follows:

```toml
[product.feature-model-reader]
name = "torte-kmax"
```
