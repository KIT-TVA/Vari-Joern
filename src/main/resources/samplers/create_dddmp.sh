#!/bin/bash
# Modified version of the script provided by Fernandez-Amoros et al. https://github.com/davidfa71/Extending-Logic
LD_LIBRARY_PATH=/BDDCreator/code/lib:$LD_LIBRARY_PATH /BDDCreator/code/bin/Logic2BDD -score sifting -line-length 70 -min-nodes 100000 -reorder-method CUDD_REORDER_SIFT -constraint-reorder minspan -base subject-noXOR  -cudd -no-static-comp "$1" "$2" >>subject-noXOR.log 2>&1
wait
rm *.data *.reorder >/dev/null 2>&1