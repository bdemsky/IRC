#!/bin/bash
trycommand () {
  $1
  if [[ ! $? ]] ; then
    echo "FAILED: $1"
    exit
  fi  
}

F=114.mp3
H=errinj-history.txt
D=errinj-diff.tmp
X=errinj-diff-ranges.tmp
T=errinj-range.tmp

trycommand "rm $H"

for (( i=0;i<100;i++))
do
  echo 'idx' $i >> $H
  trycommand "rm $X"
  echo "### make normal"
  trycommand "make normal"
  trycommand "make cleanerror"
  echo "### make error"
  trycommand "make error INV_ERROR_PROB=10000000 RANDOMSEED=90$i"
  echo "### run normal"
  trycommand "run-normal.sh $F"
  echo "### run error"
  trycommand "run-error-batchmode.sh $F $H"
  diff normal.txt error.txt > $D
  sed \
  -e '/^[^0-9]/ d' \
  -e  's/\(.*\),\(.*\)c.*/\1/' \
  -e  's/\(.*\)c.*/\1/' \
  -e  's/\(.*\)a.*/\1/' \
  $D >> $X
  if [[ -s $D ]] ; then
    awk 'NR==1;END{print}' $X > $T
    awk 'NR==1{s=$0;getline;e=$0;if(s==e) print "NO DIFF";else print s"\n"e}' $T >> $H
  else
    echo 'NO DIFF' >> $H
  fi
done


