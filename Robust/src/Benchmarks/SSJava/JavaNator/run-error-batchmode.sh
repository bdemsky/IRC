#!/bin/bash

RobotMaine.bin > error.txt

grep "SSJAVA: Injecting error" error.txt

awk '{if($1=="SSJAVA:" && $2=="Injecting"){print "inj",x};{x=$1} }' error.txt >> $1 

X=converterTempFile

sed -e '/^SSJAVA:/ d'  error.txt > $X  
#sed -e '/^SSJAVA:/ d' -e '1,/+++/ d' error.txt > $X  

mv $X error.txt
