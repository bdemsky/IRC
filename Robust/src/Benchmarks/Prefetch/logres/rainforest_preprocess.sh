#!/bin/sh

for file in `ls RainForest*`
do
  a=`echo $file | sed 's/-N/N/'`
  b1=`echo $a | cut -f1 -d"_"`
  b2=`echo $a | cut -f2 -d"_"`
  b3=`echo $a | cut -f3 -d"_"`
  b4=`echo $a | cut -f4 -d"_"`
  b5=`echo $a | cut -f5 -d"_"`
  b6=`echo $a | cut -f6 -d"_"`
  c=${b1}_${b3}_${b2}_${b4}_${b5}_${b6}
  echo $c
  mv $file $c
done
