# Command-line arguments

The basic usage of Vari-Joern is as follows:

```shell
./gradlew run --args="[options] <path to configuration file>"
```

The following options are available:

- `-s`, `--strategy`
  - Specifies the analysis strategy that should be used.
  - Either `product` for the product-based strategy or `family` for the family-based strategy.
- `-f`, `--format`
    - Specifies the output format. See [OutputFormats.md](OutputFormats.md) for more information. 
      - The product-based strategy supports the `text` and `json` formats. Default: `text`
      - The family-based strategy supports only `json` format.
- `analyzers`
    - Specifies the number of analyzer workers to run in parallel.
    - Affects only the product-based strategy.
    - Default: `1`
- `composers`
    - Specifies the number of composer workers to run in parallel.
    - Affects only the product-based strategy.
    - Default: `1`
- `--composition-queue`
  - Specifies the maximum number of compositions to keep in the queue before they are analyzed. When the queue is full,
    the composers will wait until there is space.
  - Affects only the product-based strategy.
  - Default: `1`
- `--sequential`
  - Runs the analysis strictly sequentially, without parallelization. This is different from setting the number of
    analyzers and composers to `1`, as it also prevents the composer from running in parallel with the analyzer. If this
    option is set, the `analyzers`, `composers` and `composition-queue` options do not have any effect.
  - Affects only the product-based strategy.
  - Default: `false`
- `-o`, `--output`
    - Specifies the output file. Accepted values are file paths and `-` for standard output (standard output is only
      supported by the product-based strategy).
    - Default: `-` (user's home for the family-based strategy)
- `--skip-pcs`
    - Skips the presence condition calculation.
    - Affects only the product-based strategy.
    - Default: `false`
- `--sugarlyzer-max-heap`
    - The number of gigabytes to use as the maximum heap size for every Sugarlyzer worker
    - Affects only the family-based strategy.
    - Default: `8`
- `--sugarlyzer-workers`
    - The number of concurrent workers to use for desugaring / analysis within Sugarlyzer
    - Affects only the family-based strategy.
    - Default: `1`

- `--verbose`
    - Enables verbose output.
    - Default: `false`

More options are available to configure individual components. See their documentation for more information.
