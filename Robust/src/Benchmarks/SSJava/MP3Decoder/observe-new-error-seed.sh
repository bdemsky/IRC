#!/bin/bash

usage () {
  echo 'usage:'
  echo '  observe-new-error-seed <inv prob> <random seed> <mp3 filename>'
}


if [[ -z $1 ]] ; then
  usage
  echo 'Please supply an inverse probability. (e.g. 1000)'
  exit
fi

if [[ -z $2 ]] ; then
  usage
  echo 'Please supply a random seed.'
  exit
fi

if [[ -z $3 ]] ; then
  usage
  echo 'Please supply an mp3 name. (e.g. focus.mp3)'
  exit
fi


trycommand () {
  $1
  if [[ ! $? ]] ; then
    echo "FAILED: $1"
    exit
  fi  
}


trycommand "make normal"
trycommand "make cleanerror"
trycommand "make error INV_ERROR_PROB=$1 RANDOMSEED=$2"
trycommand "run-normal.sh $3"
trycommand "run-error.sh $3"
trycommand "plot-normal-vs-error.sh"
