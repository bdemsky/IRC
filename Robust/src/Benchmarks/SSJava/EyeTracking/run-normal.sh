#!/bin/bash

LEAn.bin > normal.txt

X=converterTempFile

sed -e '/^SSJAVA:/ d'  normal.txt > $X  

mv $X normal.txt
