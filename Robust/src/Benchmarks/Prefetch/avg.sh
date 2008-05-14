#!/bin/sh

for file in `ls runlog/*.txt`
do
  echo -n $file 
  cat $file | awk '{sum += $1} END {print " "sum/NR}' 
done
