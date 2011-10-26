#!/bin/bash

if [[ -z $1 ]] ; then
  echo 'Please supply an mp3 file name.'
  exit
fi

MP3Playere.bin $1 > error.txt

grep "SSJAVA: Injecting error" error.txt

mp3samples2plotData.sh error.txt