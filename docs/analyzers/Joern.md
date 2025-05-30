# The Joern Analyzer

[Joern](https://joern.io/) is a static code analysis tool that is based on code property graphs (CPGs).
It is currently the only analyzer supported by Vari-Joern. This component uses Joern's query database to search for
vulnerabilities in the analyzed code.

## Requirements

Joern must be installed. The installation instructions can be found on the
[Joern website](https://docs.joern.io/installation/). Note that the Joern Query Database must be installed as well. This
can be done by running the following command:

```shell
joern-scan --updatedb
```

## Configuration

Add the following section to your configuration file to select the Joern analyzer:

```toml
[product.analyzer]
name = "joern"
```

No further configuration is required.

## Command line arguments

- `--joern-path`
    - Specifies the path to the Joern installation directory.
    - Optional: yes, use the system path if not specified
