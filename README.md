# Vari-Joern

Vari-Joern is an analysis platform for analyzing highly-configurable software systems for the presence of potential
vulnerabilities using the Q-SAST tool [Joern](https://joern.io).
It features two analysis strategies:
- **Optimized Product-Based Strategy**: Run Joern on a subset of all valid configurations of a configurable software 
  system as determined through a specific sampling strategy.
- **Family-Based Strategy**: Analyze a configurable software system as a whole by transforming its variable C code into
  plain C (also known as variability encoding) that can then be analyzed by Joern.

## Installation 

Vari-Joern can only be run on a Linux system.

### Native Execution (Without Docker)

Vari-Joern itself is implemented in Java and requires a JDK of version 19 or later. A corresponding open-source JDK can
be found [here](https://openjdk.org/). 

### Product-Based Strategy
Beyond a suitable JDK, Vari-Joern's product-based analysis strategy requires the following software to be installed for
native execution:
- A working installation of Joern >= 4 (e.g., version 4.0.48)
  - You may want to add Joern's executables to your `PATH` environment variable
  - Query database is already populated (e.g., via `joern-scan --updatedb --dbversion 4.0.48`)
- A working installation of curl
- A working installation of Git
- A working installation of GNU Make
- A working installation of Docker
  - Ensure that Docker runs in [rootless mode](https://docs.docker.com/engine/security/rootless/), or execute Vari-Joern
    as root.
- A working installation of gcc
- A working installation of Smarch
  - Can be installed via `pipx install git+https://github.com/KIT-TVA/Smarch.git@c573704bcfc85cc58e359926bac0143cd9ff308c`
    - This step requires g++, cmake, python3.11-dev (or later) and libgmp-dev to be installed on the system.
- A working installation nof kmax
  - Can be installed via `pipx install kmax` (see https://github.com/paulgazz/kmax)
    - This step requires python3.11-dev (or later) to be installed on the system.
- A working installation of libz3java (version 4.8.12 is known to work)
    - Package `libz3-java` on Debian and Ubuntu

Additional dependencies are required for some subject systems:
- BusyBox:
 - SELinux headers (`libselinux1-dev` on Debian/Ubuntu)
- Fiasco:
  - A working installation of flex
  - A working installation of bison
  - A working installation of g++
  - The headers of SDL (`libsdl2-dev` on Debian/Ubuntu)
- Linux:
  - A working installation of flex
  - A working installation of bison

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
Before running Vari-Joern, download the source code of the software system you want to analyze. For example, to
analyze version 1.36.1 of the BusyBox project, you can run:
```shell
git clone --branch 1_36_1 https://git.busybox.net/busybox/
```
Next, you will need to create a configuration file that specifies how Vari-Joern should analyze the source code.
See [Configuration.md](docs/Configuration.md) for more information on how to create this file.

Finally, you can run Vari-Joern to analyze the source code. Depending on whether you run Vari-Joern inside a Docker
container or natively, you need to use a different command. For either method, see [Arguments.md](docs/Arguments.md) for
a list of available command-line arguments.

### Native Execution
Vari-Joern can be run with the following command:
```shell
./gradlew run --args="-s [product/family] [further options] path/to/config.toml"
```
This will launch a product-based analysis with the configuration file `path/to/config.toml` and print a summary of the
findings to the console.

### Using the Docker container
From within the Docker container, you can run Vari-Joern as
follows:
```shell
Vari-Joern -s [product/family] [further options] path/to/config.toml
```

## Licensing
Vari-Joern is licensed under a GNU General Public License version 3 (GPLv3). More details on this license can be found 
in the [LICENSE](LICENSE) file.
Third-party software that was reused is licensed under its respective license, as indicated by the license files in the
corresponding subdirectory.
