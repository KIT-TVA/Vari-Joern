# The Antenna composer

The [Antenna composer](https://antenna.sourceforge.io/wtkpreprocess.php) is a simple preprocessor for Java source files.
This composer runs the preprocessor on each Java file, commenting out code that is not part of the selected variant.
It was added to Vari-Joern as a proof of concept and does not support all Antenna features. For example, although it
tries to determine presence conditions, it only supports a subset of Antenna's preprocessor directives, i.e., `#if`,
`#ifdef`, `#ifndef`, `#else`, `#elif`, `#elifdef`, `#elifndef`, `#endif`, and `#condition`.

## Configuration

This composer is configured using the following option:

- `source`
    - Specifies the location of the original source code which has not been preprocessed.
      Relative paths are relative to the location of the configuration file.
    - Optional: no

For example, the composer could be configured as follows:

```toml
[composer]
name = "antenna"
source = "path/to/source-code"
```
