#!/bin/sh

for file in `ls JGFSORBenchSizeD*`
do
  a=`echo $file`
  b1=`echo $a | cut -f1 -d"_"`
  b2=`echo $a | cut -f2 -d"_"`
  b3=`echo $a | cut -f3 -d"_"`
  b4=`echo $a | cut -f4 -d"_"`
  b5=`echo $a | cut -f5 -d"_"`
  c=${b1}_${b2}__${b3}_${b4}_${b5}
  mv $file $c
done
