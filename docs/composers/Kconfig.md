# The Kconfig composer

Kconfig is a variability management system used by the Linux kernel and other projects. This composer generates variants for such
codebases. It does this by determining the files that are included in the variant described by the specified
configuration. These files are then copied to a new directory, adding `#define` and `#include` directives that would be
specified using command line arguments to the compiler. Presence conditions are determined in two steps: It first
determines the presence conditions of all files that are listed in the Kbuild files. Then, it determines the presence
conditions of the individual lines. Due to the exact implementation of this last step, it is possible in very rare cases
that the composer will determine different presence conditions when composing different variants. So far, determining
presence conditions has only been implemented for BusyBox.

## Requirements

The Kconfig composer depends on [kmax 4.5.3](https://github.com/paulgazz/kmax) being installed and available in the path
variable.

## Configuration

The Kconfig composer is configured using the following options:

- `path`
    - Specifies the location of the source code.
      Relative paths are relative to the location of the configuration file.
    - Optional: no
- `encoding`
  - Specifies the encoding of the source files.
    - Optional: yes
    - Default: "utf-8"
- `system`
    - Specifies the used Kconfig/Kbuild implementation. Currently, `busybox`, `linux` and `fiasco` are supported.
    - Optional: no
- `presence_condition_excludes`
    - Specifies a list of files for which presence conditions should not be determined. The paths are relative to the
      source directory.
    - Optional: yes

For example, the composer could be configured as follows:

```toml
[composer]
name = "kconfig"
encoding = "iso-8859-1"
path = "path/to/source-code"
system = "busybox"
presence_condition_excludes = ["miscutils/setserial.c"]
```
