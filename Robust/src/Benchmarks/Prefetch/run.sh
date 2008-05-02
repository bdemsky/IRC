#!/bin/sh 

MACHINES2='dw-7.eecs.uci.edu'
MACHINES4='dw-9.eecs.uci.edu dw-5.eecs.uci.edu dw-7.eecs.uci.edu'
LOGDIR=/home/adash/research/Robust/src/Benchmarks/Prefetch/runlog
TOPDIR=`pwd`

function run {
  i=0;
  DIR=`pwd`
  while [ $i -lt $1 ]; do
    echo "$DIR" > ~/.tmpdir
    echo "bin=$3" > ~/.tmpvars
    if [ $2 -eq 1 ]; then
      arg=$ARGS1
      MACHINES=$MACHINES2
    fi
    if [ $2 -eq 2 ]; then 
      arg=$ARGS2
      MACHINES=$MACHINES2
    fi
    if [ $2 -eq 4 ]; then 
      arg=$ARGS4
      MACHINES=$MACHINES4
    fi
    chmod +x ~/.tmpvars
    for machine in `echo $MACHINES`
    do
      ssh ${machine} 'cd `cat ~/.tmpdir`; source ~/.tmpvars; ./$bin' &
      echo ""
    done
    sleep 1
    /usr/bin/time -f "%e" ./$3 master $arg 2>> ${LOGDIR}/${3}.txt
    echo "Terminating ... "
    for machine in `echo $MACHINES`
    do
      ssh ${machine} 'source ~/.tmpvars; killall $bin'
    done
    i=`expr $i + 1`
  done
}

function localrun {
  i=0;
  while [ $i -lt $1 ]; do
     #echo $ARGS1
    /usr/bin/time -f "%e" ./${NONPREFETCH} master $ARGS1 2>> ${LOGDIR}/${NONPREFETCH}.txt
    sleep 1
    #avg=`cat ${LOGDIR}/${NONPREFETCH}.txt | awk '{sum+=$1} END {print sum/NR}'`
    #sort -nr ${LOGDIR}/${NONPREFETCH}.txt | tail -1
    #sort -nr ${LOGDIR}/${NONPREFETCH}.txt | tail -1
    i=`expr $i + 1`
  done
}

function callrun {
  PREFETCH=${BENCHMARK}1.bin
  NONPREFETCH=${BENCHMARK}1NP.bin
  PREFETCH2=${BENCHMARK}2.bin
  NONPREFETCH2=${BENCHMARK}2NP.bin
  PREFETCH4=${BENCHMARK}4.bin
  NONPREFETCH4=${BENCHMARK}4NP.bin
  cd $BMDIR 

# echo "---------- Running local $BMDIR non-prefetch ---------- "
# localrun 5 

  echo "---------- Running remote $BMDIR non-prefetch 1 thread 2 machines ---------- "
  run 1 1 $NONPREFETCH
  echo "---------- Running remote $BMDIR prefetch 1 thread 2 machines ---------- "
  run 1 1 $PREFETCH

#  echo "---------- Running remote $BMDIR non-prefetch 2 machines ---------- "
#  run 5 2 $NONPREFETCH2 
#  echo "---------- Running remote $BMDIR prefetch 2 machines ---------- "
#  run 5 2 $PREFETCH2 
#
#  echo "---------- Running remote $BMDIR non-prefetch 4 machines ---------- "
#  run 5 4 $NONPREFETCH4 
#  echo "---------- Running remote $BMDIR prefetch 4 machines ---------- "
#  run 5 4 $PREFETCH4 
  cd $TOPDIR
}

benchmarks='MatrixMultiply JGFSORBenchSizeA Em3d'

for b in `echo $benchmarks`
do
  bm=`grep $b bm.txt`
  BENCHMARK=`echo $bm | cut -f1 -d":"`
  BMDIR=`echo $bm | cut -f2 -d":"`
  ARGS1=`echo $bm | cut -f3 -d":"`
  ARGS2=`echo $bm | cut -f4 -d":"`
  ARGS4=`echo $bm | cut -f5 -d":"`
  callrun
done

echo "done"
