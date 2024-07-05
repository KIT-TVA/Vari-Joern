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
    make

FROM base-system AS build
RUN apt-get install -y \
    # Required for building SuperC
    bison \
    # Required for building SuperC and Vari-Joern
    openjdk-21-jdk \
    # Required for building SuperC
    libjson-java \
    # Required for building SuperC
    sat4j \
    # Required for cloning SuperC
    git

# For running SugarC, the mergingParseErrors branch of the original SuperC is required.
RUN git clone https://github.com/appleseedlab/superc.git
WORKDIR /superc
RUN git checkout mergingParseErrors

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

RUN ./gradlew distTar

FROM base-system
RUN apt-get install -y \
    # Required by torte
    docker-ce-cli \
    # Required by torte
    git \
    # Required by Vari-Joern and Joern
    openjdk-21-jre \
    # Required for installing kmax
    pipx \
    # Required for executing kmax
    python3.11-dev \
    # Required for installing Joern
    unzip \
    # `&& exit` is necessary because otherwise `pipx ensurepath` would not be started in a new `bash` process.
    # This would then make it detect that it runs in `sh` and not in `bash` and therefore not add the necessary
    # line to the `.bashrc` file.
    && bash -c "pipx ensurepath && exit" \
    && git config --global user.email "vari-joern@example.com"

ADD https://github.com/joernio/joern/releases/latest/download/joern-install.sh /joern-install.sh
RUN chmod +x joern-install.sh \
    && /joern-install.sh --version 2.0.400 \
    && rm /joern-install.sh /joern-cli.zip
ENV PATH="/opt/joern/joern-cli:${PATH}"
RUN joern-scan --updatedb

RUN pipx install --python=$(which python3.11) kmax

COPY --from=build /vari-joern/build/distributions/Vari-Joern-1.0-SNAPSHOT.tar /Vari-Joern-1.0-SNAPSHOT.tar
RUN tar -xf /Vari-Joern-1.0-SNAPSHOT.tar -C /opt \
    && mv /opt/Vari-Joern-1.0-SNAPSHOT /opt/vari-joern \
    && rm /Vari-Joern-1.0-SNAPSHOT.tar

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
