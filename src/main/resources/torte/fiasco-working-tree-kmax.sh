#!/bin/bash
# The following line uses curl to reproducibly install and run the specified revision of torte.
# Alternatively, torte can be installed manually (see https://github.com/ekuiter/torte).
# In that case, make sure to check out the correct revision manually and run ./torte.sh <this-file>.
TORTE_REVISION=79a4df3; [[ $TOOL != torte ]] && builtin source <(curl -fsSL https://raw.githubusercontent.com/ekuiter/torte/$TORTE_REVISION/torte.sh) "$@"

export INPUT_DIRECTORY=$TORTE_INPUT_DIRECTORY
export OUTPUT_DIRECTORY=$TORTE_OUTPUT_DIRECTORY

# This experiment extracts and transforms a single feature model from a recent revision of the Linux kernel.

experiment-subjects() {
    # Inject our own variant of add-system. We don't want to clone the Linux kernel with all its history.
    # This is taken from https://github.com/ekuiter/torte/blob/79a4df311c6ccb4eec3c2b20572ecde488fbd638/scripts/utilities.sh
    add-system(system, url) {
        if [ $system != linux ]; then
            error "Only the Linux kernel should be added. This error originates from the fiasco-working-tree-kmax.sh script."
        fi
        log "git-clone: $system"
        if [[ ! -d "$(input-directory)/$system" ]]; then
            log "" "$(echo-progress clone)"
            # torte uses the kconfig implementation from v5.0
            # (see https://github.com/ekuiter/torte/blob/79a4df311c6ccb4eec3c2b20572ecde488fbd638/scripts/subjects/fiasco.sh)
            git clone "$url" "$(input-directory)/$system" --branch v5.0 --depth 1
            log "" "$(echo-done)"
        else
            log "" "$(echo-skip)"
        fi
    }
    add-linux-kconfig-binding --revision v5.0
    add-revision --system fiasco --revision vari-joern-auto-tag
    add-kconfig-model \
        --system fiasco \
        --revision vari-joern-auto-tag \
        --kconfig-file src/Kconfig \
        --kconfig-binding-file "$(linux-kconfig-binding-file v5.0)"
}

experiment-stages() {
    # Patch the broken Dockerfile until we upgrade torte
    grep -q "python3-regex" torte/docker/kmax/Dockerfile || patch torte/docker/kmax/Dockerfile kclause-Dockerfile-old.patch

    push "$INPUT_DIRECTORY"/fiasco
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
