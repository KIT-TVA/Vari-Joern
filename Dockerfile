#######################################
# Specification of base environment.
#######################################
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
    # Required for building SuperC and for executing Vari-Joern's KconfigComposer.
    make \
    # Required by Sugarlyzer.
    python3.10 python3-pip python3-apt python3.10-venv python3.10-dev

RUN apt-get install -y \
    # Required for building BDDSampler and in execution environment for fiasco.
    flex \
    # Required for building BDDSampler and SuperC/SugarC. Additionally, required in execution environment for fiasco.
    bison


#######################################
# Specification of build environment.
#######################################
FROM base-system AS build
RUN apt-get install -y \
    # Required for building SuperC and Vari-Joern
    openjdk-21-jdk \
    # Required for building SuperC
    libjson-java \
    # Required for building SuperC
    sat4j \
    # Required for building BDDSampler
    gperf \
    # Required for building BDDSampler
    libfl-dev \
    # Required for building BDDSampler
    automake \
    # Required for building BDDSampler
    autoconf \
    # Required for building BDDSampler
    libgmp3-dev

# Build SuperC.
ADD https://github.com/KIT-TVA/superc.git#a900004207849cca9ba3ae6b0e3e01bafc6b9a4a /superc
WORKDIR /superc
RUN JAVA_DEV_ROOT=/superc \
    && CLASSPATH=$CLASSPATH:$JAVA_DEV_ROOT/classes:$JAVA_DEV_ROOT/bin/junit.jar:$JAVA_DEV_ROOT/bin/antlr.jar:$JAVA_DEV_ROOT/bin/javabdd.jar:$JAVA_DEV_ROOT/bin/json-simple-1.1.1.jar \
    && CLASSPATH=$CLASSPATH:/usr/share/java/org.sat4j.core.jar:/usr/share/java/com.microsoft.z3.jar:/usr/share/java/json-lib.jar \
    && export JAVA_DEV_ROOT CLASSPATH \
    && make configure \
    && make jars \
    && unset JAVA_DEV_ROOT CLASSPATH

# Copy SuperC/SugarC jars into lib.
COPY . /vari-joern
WORKDIR /vari-joern
RUN cp /superc/bin/xtc.jar /superc/bin/superc.jar lib

# Copy external dependencies of SuperC/SugarC into lib.
RUN cp /superc/bin/junit.jar /superc/bin/antlr.jar /superc/bin/javabdd.jar /superc/bin/json-simple-1.1.1.jar lib \
    && cp /usr/share/java/org.sat4j.core.jar /usr/share/java/com.microsoft.z3.jar /usr/share/java/json-lib.jar lib

# Build Sugarlyzer.
RUN python3.10 -m venv /venv
ENV PATH=/venv/bin:$PATH
RUN python -m pip install -r requirements.txt
RUN python -m pip install build
RUN python -m build

# Installs requirements for Baital into venv.
RUN python -m pip install -r baital_requirements.txt

# Build Vari-Joern.
RUN ./gradlew distTar

# Build LS-Sampling-Plus.
ADD https://github.com/chuanluocs/LS-Sampling-Plus.git#581718a8f22df0365154e30f29e13b3318f42b3f /ls_sampling_plus
WORKDIR /ls_sampling_plus
RUN make
WORKDIR /

# Build HSCA.
ADD https://github.com/chuanluocs/HSCA.git#8d3df8ffa37f5133794249d909e3637e93797c21 /hsca
WORKDIR /hsca
RUN sh build.sh && chmod +x bin/coprocessor
WORKDIR /

# Build BDDCreator. TODO Needed?
ADD https://github.com/davidfa71/Extending-Logic.git#63ce01c9d4ab8a3604dcfcd6e85b714b6ef759bc /bddcreator
WORKDIR /bddcreator/code
ENV AUTOMAKE=:
ENV ACLOCAL=:
RUN make
WORKDIR /

# Build BDDSampler. TODO Complete (path of script?) and test
# Apparently, there is also a python wrapper that could be used: https://github.com/rheradio/bdd4va/
ADD https://github.com/davidfa71/BDDSampler.git#9d03c33c0791efbebc382b25816baa8b186ba137 /bddsampler
WORKDIR /bddsampler
RUN ./configure && make
WORKDIR /


#######################################
# Specification of final environment.
#######################################
FROM base-system
RUN apt-get update && apt-get install -y \
    # Required by BusyBox
    bzip2 \
    # Required for installing Smarch
    cmake \
    # Required by torte
    docker-ce-cli \
    # Required by torte
    git \
    # Required by Baital
    graphviz\
    # Required by BDDSampler
    m4 \
    # Required by Baital
    libboost-program-options-dev \
    # Required by Baital
    libboost-serialization-dev \
    # Required by Smarch
    libgmp-dev \
    # Required by Baital
    libmpfr-dev \
    # Required by Baital
    libmpc-dev \
    # Required by fiasco
    libsdl-dev \
    # Required by Vari-Joern and Joern
    openjdk-21-jdk \
    # Required for installing kmax
    pipx \
    # Required by BDDSampler
    perl \
    # Required for executing kmax
    python3.11-dev \
    # Useful for timing Vari-Joern runs
    time \
    # Required for installing Joern
    unzip \
    # Handy for quickly viewing and altering files within the container.
    nano \
    # Required by Baital
    z3 \
    # Required by Baital
    zlib1g-dev \
    # `&& exit` is necessary because otherwise `pipx ensurepath` would not be started in a new `bash` process.
    # This would then make it detect that it runs in `sh` and not in `bash` and therefore not add the necessary
    # line to the `.bashrc` file.
    && bash -c "pipx ensurepath && exit" \
    && git config --global user.email "vari-joern@example.com"

# Installs required for Sugarlyzer.
RUN apt-get install -y \
    selinux-basics \
    selinux-utils \
    libselinux* \
    build-essential

# Install Joern.
# TODO Update to newer Joern version (e.g., v.4.0.600)
ADD https://github.com/joernio/joern/releases/download/v4.0.407/joern-install.sh /joern-install.sh
RUN chmod +x joern-install.sh \
    && /joern-install.sh --version=v4.0.407 \
    && rm /joern-install.sh /joern-cli.zip
ENV PATH="/opt/joern/joern-cli:${PATH}"
RUN joern-scan --updatedb --dbversion 4.0.407

# Install kmax.
RUN pipx install --python=$(which python3.11) kmax


############################
# Installation of samplers.
############################

# Smarch.
RUN pipx install --python=$(which python3.11) git+https://github.com/KIT-TVA/Smarch.git@c573704bcfc85cc58e359926bac0143cd9ff308c

# Baital.
ADD https://github.com/meelgroup/baital.git#100b51da8ba8879e9b72f2bb463c1d7efe41a8f7 /samplers/baital
COPY --from=build /venv /venv
ENV PATH=/venv/bin:$PATH

# LS-Sampling-Plus.
COPY --from=build /ls_sampling_plus /samplers/ls_sampling_plus

# HSCA.
COPY --from=build /hsca /samplers/hsca
COPY --from=build /vari-joern/src/main/resources/samplers/run_HSCA.py /samplers/hsca/run_HSCA.py

# BDDCreator (used for BDDSampler).
COPY --from=build /bddcreator /samplers/bddcreator
COPY --from=build /vari-joern/src/main/resources/samplers/create_dddmp.sh /samplers/BDDSampler/create_dddmp.sh
RUN chmod +x /samplers/BDDSampler/create_dddmp.sh

# BDDSampler. TODO Test
COPY --from=build /bddsampler/bin/BDDSampler /samplers/bddsampler/bin/BDDSampler
COPY --from=build /bddsampler/lib /samplers/bddsampler/lib

# Responsible for making the lib directory available to the container.
COPY --from=build /vari-joern/build/distributions/Vari-Joern-1.0-SNAPSHOT.tar /Vari-Joern-1.0-SNAPSHOT.tar
RUN tar -xf /Vari-Joern-1.0-SNAPSHOT.tar -C /opt \
    && mv /opt/Vari-Joern-1.0-SNAPSHOT /opt/vari-joern \
    && rm /Vari-Joern-1.0-SNAPSHOT.tar

# Ensure that SuperC/SugarC and its dependencies are added to the classpath.
ENV CLASSPATH="${CLASSPATH}:/opt/vari-joern/lib/xtc.jar:/opt/vari-joern/lib/superc.jar:/opt/vari-joern/lib/junit.jar:/opt/vari-joern/lib/antlr.jar:/opt/vari-joern/lib/javabdd.jar:/opt/vari-joern/lib/json-simple-1.1.1.jar:/opt/vari-joern/lib/org.sat4j.core.jar:/opt/vari-joern/lib/com.microsoft.z3.jar:/opt/vari-joern/lib/json-lib.jar"

# Copy over built Sugarlyzer from build stage and install it.
RUN python3.10 -m pip install "setuptools<82" # Baital relies on pkg_resources which was removed from setuptools in version 82.
COPY --from=build /vari-joern/dist/sugarlyzer-0.0.1a0-py3-none-any.whl /sugarlyzer-0.0.1a0-py3-none-any.whl
RUN python3.10 -m pip install /sugarlyzer-0.0.1a0-py3-none-any.whl

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
