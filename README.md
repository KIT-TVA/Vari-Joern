# Vari-Joern

Vari-Joern is a tool for analyzing software product lines.
Its goal is to find weaknesses by running [Joern](https://joern.io) on a subset of all valid configurations of a software system.

## Usage
In the simplest case, Vari-Joern can be run with the following command:
```shell
./gradlew run --args="path/to/config.toml"
```
This will run Vari-Joern with the configuration file `path/to/config.toml` and print a summary of the findings to the
console.
For an overview of the configuration file, see [Configuration.md](docs/Configuration.md). The available command-line
arguments are described in [Arguments.md](docs/Arguments.md).
