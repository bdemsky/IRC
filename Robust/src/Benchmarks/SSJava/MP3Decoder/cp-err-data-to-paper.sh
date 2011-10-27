#!/bin/bash

if [[ -z $1 ]] ; then
cp normal.txt error.txt nve-diff-ranges.tmp ~/research/Papers/12/pldi.ssjava/err-inj-fig
else 
cp normal.txt error.txt nve-diff-ranges.tmp $1/Papers/12/pldi.ssjava/err-inj-fig
fi
