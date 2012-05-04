#!/bin/bash

CURDIR=`pwd`
SINGF=output.sing
DOJF=output.doj
DIF=$CURDIR/validation-result.txt
rm -f $DIF

BDIR=( raytracer barneshut tracking voronoi kmeans power )
BCOM=( test Barneshut TrackingBench TestRunner KMeans Power )
BARGS=( "1 1" "./inputs/BarnesHutLarge.in 1" "" "1000000 -p" "-m 40 -n 40 -t 0.00001 -i inputs/random-n65536-d32-c16.txt -nthreads 1 -v"  "1" )

for((i=0; i<${#BDIR[@]}; i++))
do
cd $CURDIR/${BDIR[$i]}
  rm -f $SINGF
  rm -f $DOJF
  make clean;
  make single;make rcrpointer
  ./${BCOM[$i]}s.bin ${BARGS[$i]} > $SINGF
  ./${BCOM[$i]}r.bin ${BARGS[$i]} > $DOJF
  echo ${BDIR[$i]} >> $DIF
  diff $SINGF $DOJF >> $DIF  
  rm -f $SINGF
  rm -f $DOJF
  make clean;
done

BDIR2=( crypt monte moldyn labyrinth sor mergesort )
BCOM2=( JGFCryptBench JGFMonteCarloBench JGFMolDynBenchSizeB Labyrinth JGFSORBenchSizeD MergeSort4 )
BARGS2=( "2 43 1" "" "1 215" "-w 22 -i ./inputs/random-x7-y512-z512-n512.txt" "" "134217728 32 1" )

for((i=0; i<${#BDIR2[@]}; i++))
do
cd $CURDIR/${BDIR2[$i]}
  make rcrpointer
  ./${BCOM2[$i]}r.bin ${BARGS2[$i]} > $DOJF
  if [ $(grep -c VALID $DOJF) -ne 0 ]
  then
    echo ${BDIR2[$i]} >> $DIF    
  else
    echo ${BDIR2[$i]} "FAIL" >> $DIF  
  fi
  rm -f $DOJF
  make clean;
done