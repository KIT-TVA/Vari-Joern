#!/bin/bash
# The following line uses curl to reproducibly install and run the specified revision of torte.
# Alternatively, torte can be installed manually (see https://github.com/ekuiter/torte).
# In that case, make sure to check out the correct revision manually and run ./torte.sh <this-file>.
TORTE_REVISION=79a4df3; [[ $TOOL != torte ]] && builtin source <(curl -fsSL https://raw.githubusercontent.com/ekuiter/torte/$TORTE_REVISION/torte.sh) "$@"

export INPUT_DIRECTORY=$TORTE_INPUT_DIRECTORY
export OUTPUT_DIRECTORY=$TORTE_OUTPUT_DIRECTORY

# This experiment extracts and transforms a single feature model from a recent revision of the Linux kernel.

experiment-subjects() {
    add-linux-kconfig vari-joern-auto-tag
}

experiment-stages() {
    # Patch the broken Dockerfile until we upgrade torte
    grep -q "python3-regex" torte/src/docker/kclause/Dockerfile || patch torte/docker/kmax/Dockerfile kclause-Dockerfile-old.patch

    push "$INPUT_DIRECTORY"/linux
    if [ ! -d .git ]; then
        git init
    fi
    git add .
    if [ -n "$(git status --porcelain)" ]; then
        git commit -m "Automatic commit by Vari-Joern"
    fi
    git tag -f vari-joern-auto-tag
    pop

    extract-kconfig-models-with kmax

    # transform
    transform-models-with-featjar --transformer model_to_xml_featureide --output-extension xml --jobs 2
}
