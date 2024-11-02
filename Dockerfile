FROM ubuntu:noble AS base-system
# Prepare for installation of Docker and Python 3.11
RUN apt-get update && apt-get install -y \
    ca-certificates \
    curl \
    software-properties-common \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
    tee /etc/apt/sources.list.d/docker.list > /dev/null \
    && add-apt-repository ppa:deadsnakes/ppa \
    && apt-get update \
    && apt-get install -y \
    # Required for building SuperC and for executing Vari-Joern. Keep this in the base image, as this ensures that both
    # the build and the execution environment are the same. This is important for SuperC, because it assumes that it runs
    # in the same environment as it was built in.
    g++ \
    gcc \
    # Required for building and running SuperC
    libz3-java=4.8.12-3.1build1 \
    # Required for building SuperC and for executing Vari-Joern's KBuildComposer.
    make \
    # Required by Sugarlyzer.
    python3.10 python3-pip python3-apt python3.10-venv

FROM base-system AS build
RUN apt-get install -y \
    # Required for building SuperC
    bison \
    # Required for building SuperC and Vari-Joern
    openjdk-21-jdk \
    # Required for building SuperC
    libjson-java \
    # Required for building SuperC
    sat4j

ADD https://github.com/KIT-TVA/superc.git#961120cdf4715b48d253d95e605494ae5ccea035 /superc
WORKDIR /superc
RUN JAVA_DEV_ROOT=/superc \
    && CLASSPATH=$CLASSPATH:$JAVA_DEV_ROOT/classes:$JAVA_DEV_ROOT/bin/junit.jar:$JAVA_DEV_ROOT/bin/antlr.jar:$JAVA_DEV_ROOT/bin/javabdd.jar:$JAVA_DEV_ROOT/bin/json-simple-1.1.1.jar \
    && CLASSPATH=$CLASSPATH:/usr/share/java/org.sat4j.core.jar:/usr/share/java/com.microsoft.z3.jar:/usr/share/java/json-lib.jar \
    && export JAVA_DEV_ROOT CLASSPATH \
    && make configure \
    && make jars \
    && unset JAVA_DEV_ROOT CLASSPATH

COPY . /vari-joern
WORKDIR /vari-joern
RUN cp /superc/bin/xtc.jar /superc/bin/superc.jar lib

# External dependencies of SuperC/SugarC.
RUN cp /superc/bin/junit.jar /superc/bin/antlr.jar /superc/bin/javabdd.jar /superc/bin/json-simple-1.1.1.jar lib \
    && cp /usr/share/java/org.sat4j.core.jar /usr/share/java/com.microsoft.z3.jar /usr/share/java/json-lib.jar lib

# Build Sugarlyzer.
RUN python3.10 -m venv /venv
ENV PATH=/venv/bin:$PATH
RUN python -m pip install -r requirements.txt
RUN python -m pip install build
RUN python -m build

RUN ./gradlew distTar

FROM base-system
RUN apt-get update && apt-get install -y \
    # Required by fiasco
    bison \
    # Required by BusyBox
    bzip2 \
    # Required for installing Smarch
    cmake \
    # Required by torte
    docker-ce-cli \
    # Required by fiasco
    flex \
    # Required by torte
    git \
    # Required by Smarch
    libgmp-dev \
    # Required by fiasco
    libsdl-dev \
    # Required by Vari-Joern and Joern
    openjdk-21-jdk \
    # Required for installing kmax
    pipx \
    # Required for executing kmax
    python3.11-dev \
    # Useful for timing Vari-Joern runs
    time \
    # Required for installing Joern
    unzip \
    # `&& exit` is necessary because otherwise `pipx ensurepath` would not be started in a new `bash` process.
    # This would then make it detect that it runs in `sh` and not in `bash` and therefore not add the necessary
    # line to the `.bashrc` file.
    && bash -c "pipx ensurepath && exit" \
    && git config --global user.email "vari-joern@example.com"

# Installs required for Sugarlyzer.
RUN apt-get install -y selinux-basics selinux-utils libselinux*

ADD https://github.com/joernio/joern/releases/latest/download/joern-install.sh /joern-install.sh
RUN chmod +x joern-install.sh \
    && /joern-install.sh --version=v4.0.48 \
    && rm /joern-install.sh /joern-cli.zip
ENV PATH="/opt/joern/joern-cli:${PATH}"
RUN joern-scan --updatedb --dbversion 4.0.48

RUN pipx install --python=$(which python3.11) kmax git+https://github.com/duckymirror/Smarch.git@c573704bcfc85cc58e359926bac0143cd9ff308c

# Responsible for making the lib directory available to the container.
COPY --from=build /vari-joern/build/distributions/Vari-Joern-1.0-SNAPSHOT.tar /Vari-Joern-1.0-SNAPSHOT.tar
RUN tar -xf /Vari-Joern-1.0-SNAPSHOT.tar -C /opt \
    && mv /opt/Vari-Joern-1.0-SNAPSHOT /opt/vari-joern \
    && rm /Vari-Joern-1.0-SNAPSHOT.tar

# Ensure that SuperC/SugarC and its dependencies are added to the classpath.
ENV CLASSPATH="${CLASSPATH}:/opt/vari-joern/lib/xtc.jar:/opt/vari-joern/lib/superc.jar:/opt/vari-joern/lib/junit.jar:/opt/vari-joern/lib/antlr.jar:/opt/vari-joern/lib/javabdd.jar:/opt/vari-joern/lib/json-simple-1.1.1.jar:/opt/vari-joern/lib/org.sat4j.core.jar:/opt/vari-joern/lib/com.microsoft.z3.jar:/opt/vari-joern/lib/json-lib.jar"

RUN python3.10 -m pip install --upgrade setuptools
COPY --from=build /vari-joern/dist/Sugarlyzer-0.0.1a0-py3-none-any.whl /Sugarlyzer-0.0.1a0-py3-none-any.whl
RUN python3.10 -m pip install /Sugarlyzer-0.0.1a0-py3-none-any.whl

# If the Docker daemon has been started in rootless mode, torte runs `whoami` to ensure that it does not run as root.
# If the daemon has not been started in rootless mode, torte wants to run as root. We can fake the user by overriding
# the `whoami` command.
RUN mkdir /root/overrides && cat <<EOF > /root/overrides/whoami && chmod +x /root/overrides/whoami
#!/bin/sh
if docker info -f "{{println .SecurityOptions}}" | grep -q rootless; then
    echo "absolutely-not-root"
else
    echo "root"
fi
EOF

ENV PATH="/root/overrides:/opt/vari-joern/bin:${PATH}"
RUN mkdir /subject
WORKDIR /subject
CMD [ "bash" ]
