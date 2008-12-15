#!/bin/sh 

#set -x
LOGDIR=/home/adash/research/Robust/src/Benchmarks/Prefetch/runlog
TOPDIR=`pwd`
function javasinglerun {
  i=0;
  while [ $i -lt $1 ]; do
    /usr/bin/time -f "%e" ./${BENCHMARK}.bin $ARGS1 2>> ${LOGDIR}/${BENCHMARK}_javasingle_${EXTENSION}.txt
    sleep 2
    i=`expr $i + 1`
  done
}

function callrun {
 cd $BMDIR 

  echo "---------- Running javasingle version $BMDIR on 1 machine ---------- "
  javasinglerun 1 
  cd $TOPDIR
}

########## Java single benchmarks #############
benchmarks='40962dconv 20482dconv mmver600 moldynverA 1152fft2d'

echo "---------- Clean old files ---------- "
rm runlog/*
for b in `echo $benchmarks`
do
  bm=`grep $b bmlatest.txt`
  BENCHMARK=`echo $bm | cut -f1 -d":"`
  BMDIR=`echo $bm | cut -f2 -d":"`
  ARGS1=`echo $bm | cut -f3 -d":"`
  EXTENSION=`echo $bm | cut -f4 -d":"`
  callrun
done

#----------Calulates  the averages ----------- 
for file in `ls runlog/*.txt`
do
  echo -n $file >> average.txt
  cat $file | grep -v "^Command" | awk '{sum += $1} END {print " "sum/NR}' >> average.txt
done
echo "===========" >> average.txt
echo "" >> average.txt

echo "done"
