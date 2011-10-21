#!/bin/bash

X=converterTempFile

# first gobble up any lines of SSJAVA talk
# then the benchmark's preamble up to the sentinel: +++
sed -e '/^SSJAVA:/ d' -e '1,/+++/ d' $1 > $X  

mv $X $1
