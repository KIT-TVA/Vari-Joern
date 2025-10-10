#!/bin/bash
# The following line uses curl to reproducibly install and run the specified revision of torte.
# Alternatively, torte can be installed manually (see https://github.com/ekuiter/torte).
# In that case, make sure to check out the correct revision manually and run ./torte.sh <this-file>.
TORTE_REVISION=v1.0.0; [[ $TOOL != torte ]] && builtin source <(curl -fsSL https://raw.githubusercontent.com/ekuiter/torte/$TORTE_REVISION/torte.sh) "$@"

experiment-systems() {
    add-revision --system busybox --revision vari-joern-auto-tag
    add-kconfig --system busybox --revision vari-joern-auto-tag --kconfig-file Config.in \
        --kconfig-binding-files scripts/kconfig/*.o
}

experiment-stages() {
    push "$TORTE_INPUT_DIRECTORY"/busybox
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
    mv -T "$TORTE_INPUT_DIRECTORY/busybox" "$(stage-path clone-systems busybox)"
    touch "$(stage-done-file clone-systems)"

    read-busybox-configs

    extract-kconfig-models-with --extractor kclause

    # transform
    transform-model-with-featjar \
      --transformer model-to-xml-with-featureide \
      --input extract-kconfig-models-with-kclause \
      --output-extension xml \
      --jobs 2

    # Copy to output directory
    cp -r "$(stage-path model-to-xml-with-featureide busybox)" "$TORTE_OUTPUT_DIRECTORY"
    cp -r "$(stage-path extract-kconfig-models-with-kclause busybox)" "$TORTE_OUTPUT_DIRECTORY/kconfig"
}
