# The Kbuild composer

Kbuild is a build system used by the Linux kernel and other projects. This composer generates variants for such
codebases. It does this by determining the files that are included in the variant described by the specified
configuration. These files are then copied to a new directory, adding `#define` and `#include` directives that Kbuild
would specify using command line arguments to the compiler. Presence conditions are determined in two steps: It first
determines the presence conditions of all files that are listed in the Kbuild files. Then, it determines the presence
conditions of the individual lines. Due to the exact implementation of this last step, it is possible in very rare cases
that the composer will determine different presence conditions when composing different variants. So far, determining
presence conditions has only been implemented for BusyBox.

## Requirements

The Kbuild composer depends on [kmax 4.5.3](https://github.com/paulgazz/kmax) being installed and available in the path
variable.

## Configuration

The Kbuild composer is configured using the following options:

- `source`
    - Specifies the location of the source code.
      Relative paths are relative to the location of the configuration file.
    - Optional: no
- `system`
    - Specifies the used Kconfig/Kbuild implementation. Currently, `busybox` and `linux` are supported.
    - Optional: no

For example, the composer could be configured as follows:

```toml
[composer]
name = "kbuild"
source = "path/to/source-code"
system = "busybox"
```
