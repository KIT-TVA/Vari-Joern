#!/bin/bash
# The following line uses curl to reproducibly install and run the specified revision of torte.
# Alternatively, torte can be installed manually (see https://github.com/ekuiter/torte).
# In that case, make sure to check out the correct revision manually and run ./torte.sh <this-file>.
TORTE_REVISION=v1.0.0; [[ $TOOL != torte ]] && builtin source /dev/stdin <<<"$(curl -fsSL https://raw.githubusercontent.com/ekuiter/torte/$TORTE_REVISION/torte.sh)" "$@"

experiment-systems() {
    # Inject our own variant of add-system. We don't want to clone the Linux kernel with all its history.
    # This is taken from https://github.com/ekuiter/torte/blob/79a4df311c6ccb4eec3c2b20572ecde488fbd638/scripts/utilities.sh
    add-system(system, url) {
        if [ $system != linux ]; then
            error "Only the Linux kernel should be added. This error originates from the toybox-working-tree-kmax.sh script."
        fi
        log "git-clone: $system"
        if [[ ! -d "$(input-directory)/$system" ]]; then
            log "" "$(echo-progress clone)"
            # torte uses the kconfig implementation from v6.7
            # (see https://github.com/ekuiter/torte/blob/v1.0.0/src/systems/toybox.sh)
            git clone "$url" "$(input-directory)/$system" --branch v6.7 --depth 1
            log "" "$(echo-done)"
        else
            log "" "$(echo-skip)"
        fi
    }

    add-hook-step kconfig-post-checkout-hook toybox "$(to-lambda kconfig-post-checkout-hook-toybox)"
    add-linux-kconfig-binding --revision v6.7
    add-revision --system toybox --revision vari-joern-auto-tag
    add-kconfig-model \
        --system toybox \
        --revision vari-joern-auto-tag \
        --kconfig-file Config.in \
        --kconfig-binding-file "$(linux-kconfig-binding-file v6.7)"
}

kconfig-post-checkout-hook-toybox(system, revision) {
    if [[ $system == toybox ]]; then
      make generated/Config.probed
      sed -Ei 's/(\W+bool )([a-zA-Z0-9]+)/\1"\2"/' generated/Config.in
    fi
}

experiment-stages() {
    push "$TORTE_INPUT_DIRECTORY"/toybox
    sed -Ei 's/source *([^# ]+)/source "\1"/' Config.in
    if [ ! -d .git ]; then
        git init
    fi
    git add .
    if [ -n "$(git status --porcelain)" ]; then
        git commit -m "Automatic commit by Vari-Joern"
    fi
    git tag -f vari-joern-auto-tag
    pop

    mkdir -p "$(stage-path clone-systems)"
    mv -T "$TORTE_INPUT_DIRECTORY/toybox" "$(stage-path clone-systems toybox)"
    touch "$(stage-done-file clone-systems)"

    read-toybox-configs

    extract-kconfig-models-with --extractor kclause

    # transform
    transform-model-with-featjar --transformer model-to-xml-with-featureide --input extract-kconfig-models-with-kclause --output-extension xml --jobs 2

    # Copy to output directory
    cp -r "$(stage-path model-to-xml-with-featureide toybox)" "$TORTE_OUTPUT_DIRECTORY"
    cp -r "$(stage-path extract-kconfig-models-with-kclause toybox)" "$TORTE_OUTPUT_DIRECTORY/kconfig"
}
