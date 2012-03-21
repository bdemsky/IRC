#!/bin/bash

usage () {
  echo 'usage:'
  echo '  observe-new-error-seed <inv prob> <random seed> '
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

trycommand () {
  $1
  if [[ ! $? ]] ; then
    echo "FAILED: $1"
    exit
  fi  
}

trycommand "make cleanerror"
trycommand "make error INV_ERROR_PROB=$1 RANDOMSEED=$2"
trycommand "run-error-batchmode.sh output.tmp"
trycommand "cat output.tmp"
trycommand "rm output.tmp"

