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

### Using Docker
Vari-Joern can also be run using Docker. To do so, first build the Docker image, for example with the following command:
```shell
docker build --ssh default -t vari-joern .
```
Make sure that `ssh-agent` is running and set up with a key that has access to
[the SuperC repository](https://github.com/KIT-TVA/superc). See the
[GitHub documentation](https://docs.github.com/en/authentication/connecting-to-github-with-ssh) for more information.


Then run the image to enter the container:
```shell
docker run -it -v /path/to/source:/subject -v /path/to/docker.sock:/var/run/docker.sock -v /tmp:/tmp vari-joern
```
Replace `/path/to/source` with the path to the source code that you want to analyze and `/path/to/docker.sock` with the
path to the Docker socket on the host system. It is usually located at `/var/run/docker.sock` or
`$XDG_RUNTIME_DIR/docker.sock`.
