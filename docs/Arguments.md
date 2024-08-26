# Command-line arguments

The basic usage of Vari-Joern is as follows:

```shell
./gradlew run --args="[options] <path to configuration file>"
```

The following options are available:

- `--composition-queue`
    - Specifies the maximum number of compositions to keep in the queue before they are analyzed. If the queue is full,
      the composer will wait until there is space.
    - Default: `1`
- `-f`, `--format`
    - Specifies the output format. Vari-Joern supports the `text` and `json` formats.
      See [OutputFormats.md](OutputFormats.md) for more information.
    - Default: `text`
- `analyzers`
    - Specifies the number of analyzers to run in parallel.
    - Default: `1`
- `composers`
    - Specifies the number of composers to run in parallel.
    - Default: `1`
- `-o`, `--output`
    - Specifies the output file. Accepted values are file paths and `-` for standard output.
    - Default: `-`
- `--skip-pcs`
    - Skips the presence condition calculation.
    - Default: `false`
- `--verbose`
    - Enables verbose output.
    - Default: `false`

More options are available to configure individual components. See their documentation for more information.
