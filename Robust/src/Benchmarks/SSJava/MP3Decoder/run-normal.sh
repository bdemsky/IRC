#!/bin/bash

if [[ -z $1 ]] ; then
  echo 'Please supply an mp3 file name.'
  exit
fi

MP3Playern.bin $1 > normal.txt

mp3samples2plotData.sh normal.txt