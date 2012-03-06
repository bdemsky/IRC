#!/bin/bash

if [[ -z $1 ]] ; then
  echo 'Please supply an mp3 file name.'
  exit
fi

MP3Playere.bin $1 > error.txt

grep "SSJAVA: Injecting error" error.txt

awk '{if($1=="SSJAVA:" && $2=="Injecting"){print "inj",x};{x=$1} }' error.txt >> $2 

X=converterTempFile

sed -e '/^SSJAVA:/ d' -e '1,/+++/ d' error.txt > $X  

mv $X error.txt
