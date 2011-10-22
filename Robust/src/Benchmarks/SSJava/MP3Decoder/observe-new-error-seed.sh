#!/bin/bash

if [[ -z $1 ]] ; then
  echo 'Please supply an inverse probability (e.g. 1000).'
  exit
fi

if [[ -z $2 ]] ; then
  echo 'Please supply a random seed.'
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
trycommand "run-normal.sh"
trycommand "run-error.sh"
trycommand "plot-normal-vs-error.sh"
