# The Kconfig composer

Kconfig is a variability management system used by the Linux kernel and other projects.
This composer generates variants for codebases that are built on this system.
It does this by determining the files that are included in the variant described by the sampled configuration.
These files are then copied to a new directory, adding `#define` and `#include` directives that would be
specified using command line arguments to the compiler.
For BusyBox, the composer also supports presence condition extraction.
For details on how the composer works and its limitations, see the Javadoc comment for the
[KconfigComposer class](../../src/main/java/edu/kit/varijoern/composers/kconfig/KconfigComposer.java).
Due to the differences in the Kconfig implementations, the composer checks the `name` option of the subject section to
adapt to the used implementation. It currently supports the Kconfig implementations of the Linux kernel
(`name = "linux"`), BusyBox (`name = "busybox"`), Fiasco (`name = "fiasco"`), axTLS (`name = "axtls"`),
and Toybox (`name = "toybox"`).

## Requirements

The Kconfig composer depends on [kmax](https://github.com/paulgazz/kmax) 4.5.3 or later being installed and available in the path
variable.

## Configuration

The Kconfig composer is configured using the following options:

- `path`
    - Specifies the location of the source code.
      Relative paths are relative to the root location of the subject.
    - Optional: yes
    - Default: `.`
- `encoding`
  - Specifies the encoding of the source files.
    - Optional: yes
    - Default: "utf-8"
- `presence_condition_excludes`
    - Specifies a list of files for which presence conditions should not be determined. The paths are relative to the
      source directory specified by the `path` field.
    - Optional: yes

For example, the configuration for BusyBox would typically look like this:

```toml
[product.composer]
name = "kconfig"
encoding = "iso-8859-1"
presence_condition_excludes = ["miscutils/setserial.c"]
```
