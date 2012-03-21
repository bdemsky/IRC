#!/bin/bash

usage () {
  echo 'usage:'
  echo '  run-batch-mode <random seed>'
}

if [[ -z $1 ]] ; then
  usage
  echo 'Please supply the initial random seed. (e.g. 9090)'
  exit
fi

trycommand () {
  $1
  if [[ ! $? ]] ; then
    echo "FAILED: $1"
    exit
  fi  
}

F=114.mp3
H=errinj-history-$1.txt
D=errinj-diff.tmp
X=errinj-diff-ranges.tmp
T=errinj-range.tmp

trycommand "rm $H"

max=$(($1+1))

echo "### make normal"
trycommand "make normal"
echo "### run normal"
trycommand "run-normal.sh $F"

for (( i=$1;i<max;i++))
do
  echo 'idx' $i >> $H
  trycommand "rm $X"
  trycommand "make cleanerror"
  echo "### make error"
  trycommand "make error INV_ERROR_PROB=10000000 RANDOMSEED=$i"
  echo "### run error"
  trycommand "run-error-batchmode.sh $F $H"
  diff normal.txt error.txt > $D
  sed \
  -e '/^[^0-9]/ d' \
  -e  's/\(.*\),\(.*\)c.*/\1\n\2/' \
  -e  's/\(.*\)c.*/\1/' \
  -e  's/\(.*\)a.*/\1/' \
  $D >> $X
  if [[ -s $D ]] ; then
    awk 'NR==1;END{print}' $X >> $H
    #awk 'NR==1{s=$0;getline;e=$0;if(s==e) print s;else print s"\n"e}' $T >> $H
  else
    echo 'NO DIFF' >> $H
  fi
done


