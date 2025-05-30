# Vari-Joern

Vari-Joern is an analysis platform for analyzing highly-configurable software systems for the presence of potential
vulnerabilities using the Q-SAST tool [Joern](https://joern.io).
It features two analysis strategies:
- **Optimized Product-Based Strategy**: Run Joern on a subset of all valid configurations of a configurable software 
  system as determined through a specific sampling strategy.
- **Family-Based Strategy**: Analyze a configurable software system as a whole by transforming its variable C code into
  plain C (also known as variability encoding) that can then be analyzed by Joern.

## Installation 

### General Prerequisites (Native execution and Docker container)
- Clone the repository using `git clone`
- Initialize & update the contained  `Antenna` submodule. 
  - Change into Vari-Joern's root directory (`cd <target_location>/Vari-Joern`).
  - Execute ``git submodule init`` followed by `git submodule update`

### Native Execution (Without Docker)

Vari-Joern itself is implemented in Java and requires a JDK of version 19 or later. A corresponding open-source JDK can
be found [here](https://openjdk.org/). 

### Product-Based Strategy
Beyond a suitable JDK, Vari-Joern's product-based analysis strategy requires the following software to be installed for
native execution:
- *TODO*

#### Family-Based Strategy
Beyond a suitable JDK, Vari-Joern's family-based analysis strategy requires the following software to be installed for
native execution:
- A working installation of [KIT-TVA/superc](https://github.com/KIT-TVA/superc)
  - Corresponding jars are expected to be part of the `PATH` environment variable (the `java superc.SugarC` command 
    should launch SuperC)
  - See the [install_superc.bash](scripts/install_superc.bash) script
- A working installation of Joern >= 4 (e.g., version 4.0.48)
  - `joern-cli` is expected to be part of the `PATH` environment variable (the `joern` command should launch Joern)
  - The query database is expected to be already populated (e.g., via `joern-scan --updatedb --dbversion 4.0.48`)
- A working installation of a C compiler (preferably GCC as clang has not been tested)
- Python 3 (>= 3.10.0)
  - The `python` command should point to Python 3, not to Python 2 (can be solved via a symbolic link or alias). 
  - Pip is installed (can be installed via `sudo apt install python3-pip`)
  - Python dependencies are installed (can be installed with `python -m pip install -r requirements.txt`)
- `PYTHONPATH` points to `Vari-Joern/src/main`
- A working installation nof kmax
  - Can be installed via `pipx install kmax` (see https://github.com/paulgazz/kmax) 

### Using the Docker container
Vari-Joern can be run using Docker. To do so, first build the Docker image, for example with the following command,
executed from the repo's root directory:
```shell
docker build -t vari-joern .
```

Then run the image to enter the container:
```shell
docker run -it -v /path/to/source:/subject -v /path/to/docker.sock:/var/run/docker.sock -v /tmp:/tmp vari-joern
```
Replace `/path/to/source` with the path to the source code that you want to analyze and `/path/to/docker.sock` with the
path to the Docker socket on the host system. It is usually located at `/var/run/docker.sock` or
`$XDG_RUNTIME_DIR/docker.sock`. This command will start a shell in the container.


## Usage

### Native Execution
In the simplest case, Vari-Joern can be run with the following command:
```shell
./gradlew run --args="path/to/config.toml"
```
This will run Vari-Joern with the configuration file `path/to/config.toml` and print a summary of the findings to the
console.
For an overview of the configuration file, see [Configuration.md](docs/Configuration.md). The available command-line
arguments are described in [Arguments.md](docs/Arguments.md).

### Using the Docker container
From within the Docker container, you can run Vari-Joern as
follows:
```shell
Vari-Joern [options] path/to/config.toml
```

Again, for an overview of the configuration file and available options see [Configuration.md](docs/Configuration.md) and [Arguments.md](docs/Arguments.md).


## Licensing
Vari-Joern is licensed under a GNU General Public License version 3 (GPLv3). More details on this license can be found 
in the [LICENSE](LICENSE) file.
Third-party software that was reused is licensed under its respective license, as indicated by the license files in the
corresponding subdirectory.