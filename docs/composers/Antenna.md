# The Antenna Composer

The Antenna composer wraps the [Antenna preprocessor](https://antenna.sourceforge.io/wtkpreprocess.php), a simple
preprocessor for Java source files.
The composer runs the preprocessor on each Java file, commenting out code that is not part of the selected variant.
It was added to Vari-Joern as a proof of concept and does not support all Antenna features. For example, although it
tries to determine presence conditions, it only supports a subset of Antenna's preprocessor directives, namely `#if`,
`#ifdef`, `#ifndef`, `#else`, `#elif`, `#elifdef`, `#elifndef`, `#endif`, and `#condition`.

## Configuration

This composer is configured using the following option:

- `source`
    - Specifies the location of the original source code, which has not been preprocessed.
      Relative paths are relative to the root location of the subject.
    - Optional: yes
    - Default: `.`

For example, the composer could be configured as follows:

```toml
[product.composer]
name = "antenna"
source = "path/to/source-code"
```
