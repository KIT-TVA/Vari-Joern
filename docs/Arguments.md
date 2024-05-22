# Command-line arguments

The basic usage of Vari-Joern is as follows:

```shell
./gradlew run --args="[options] <path to configuration file>"
```

The following options are available:

- `-f`, `--format`
    - Specifies the output format. Vari-Joern supports the `text` and `json` formats.
      See [OutputFormats.md](OutputFormats.md) for more information.
    - Default: `text`
- `-o`, `--output`
    - Specifies the output file. Accepted values are file paths and `-` for standard output.
    - Default: `-`
- `--verbose`
    - Enables verbose output.
    - Default: `false`

More options are available to configure individual components. See their documentation for more information.
