#!/bin/sh 

#set -x
MACHINES2='dw-11.eecs.uci.edu'
MACHINES3='dw-11.eecs.uci.edu dw-12.eecs.uci.edu'
MACHINES4='dw-11.eecs.uci.edu dw-12.eecs.uci.edu dw-13.eecs.uci.edu'
MACHINES5='dw-11.eecs.uci.edu dw-12.eecs.uci.edu dw-13.eecs.uci.edu dw-14.eecs.uci.edu'
MACHINES6='dw-11.eecs.uci.edu dw-12.eecs.uci.edu dw-13.eecs.uci.edu dw-14.eecs.uci.edu dw-15.eecs.uci.edu'
MACHINES7='dw-11.eecs.uci.edu dw-12.eecs.uci.edu dw-13.eecs.uci.edu dw-14.eecs.uci.edu dw-15.eecs.uci.edu dw-16.eecs.uci.edu'
MACHINES8='dw-11.eecs.uci.edu dw-12.eecs.uci.edu dw-13.eecs.uci.edu dw-14.eecs.uci.edu dw-15.eecs.uci.edu dw-16.eecs.uci.edu dw-17.eecs.uci.edu'
LOGDIR=/home/adash/research/Robust/src/Benchmarks/Prefetch/runlog
TOPDIR=`pwd`

function run {
  i=0;
  DIR=`pwd`
  while [ $i -lt $1 ]; do
    echo "$DIR" > ~/.tmpdir
    echo "bin=$3" > ~/.tmpvars
    if [ $2 -eq 2 ]; then 
      arg=$ARGS2
      MACHINES=$MACHINES2
    fi
    if [ $2 -eq 3 ]; then 
      arg=$ARGS3
      MACHINES=$MACHINES3
    fi
    if [ $2 -eq 4 ]; then 
      arg=$ARGS4
      MACHINES=$MACHINES4
    fi
    if [ $2 -eq 5 ]; then 
      arg=$ARGS5
      MACHINES=$MACHINES5
    fi
    if [ $2 -eq 6 ]; then 
      arg=$ARGS6
      MACHINES=$MACHINES6
    fi
    if [ $2 -eq 7 ]; then 
      arg=$ARGS7
      MACHINES=$MACHINES7
    fi
    if [ $2 -eq 8 ]; then 
      arg=$ARGS8
      MACHINES=$MACHINES8
    fi
    chmod +x ~/.tmpvars
    for machine in `echo $MACHINES`
    do
      ssh ${machine} 'cd `cat ~/.tmpdir`; source ~/.tmpvars; ./$bin' &
      echo ""
    done
    sleep 2 
    /usr/bin/time -f "%e" ./$3 master $arg 2>> ${LOGDIR}/${3}.txt
    echo "Terminating ... "
    for machine in `echo $MACHINES`
    do
      ssh ${machine} 'source ~/.tmpvars; killall $bin'
    done
    sleep 2
    i=`expr $i + 1`
  done
}

function localrun {
  i=0;
  while [ $i -lt $1 ]; do
    /usr/bin/time -f "%e" ./${NONPREFETCH} master $ARGS1 2>> ${LOGDIR}/${NONPREFETCH}_local.txt
    sleep 4
    i=`expr $i + 1`
  done
}

function callrun {
  NONPREFETCH=${BENCHMARK}1NP.bin
  NONPREFETCH2=${BENCHMARK}2NP.bin
  NONPREFETCH3=${BENCHMARK}3NP.bin
  NONPREFETCH4=${BENCHMARK}4NP.bin
  NONPREFETCH5=${BENCHMARK}5NP.bin
  NONPREFETCH6=${BENCHMARK}6NP.bin
  NONPREFETCH7=${BENCHMARK}7NP.bin
  NONPREFETCH8=${BENCHMARK}8NP.bin

  echo "---------- Running ${BENCHMARK} local non-prefetch on 1 machine ---------- "
  localrun 10

  echo "---------- Running ${BENCHMARK} two threads non-prefetch on 2 machines ---------- "
  run 10 2 $NONPREFETCH2 
  echo "---------- Running ${BENCHMARK} three threads non-prefetch on 3 machines ---------- "
  run 10 3 $NONPREFETCH3 
  echo "---------- Running ${BENCHMARK} four threads non-prefetch on 4 machines ---------- "
  run 5 4 $NONPREFETCH4 
  echo "---------- Running ${BENCHMARK} five threads non-prefetch on 5 machines ---------- "
  run 10 5 $NONPREFETCH5 
  echo "---------- Running ${BENCHMARK} six threads non-prefetch on 6 machines ---------- "
  run 10 6 $NONPREFETCH6 
  echo "---------- Running ${BENCHMARK} seven threads non-prefetch on 7 machines ---------- "
  run 10 7 $NONPREFETCH7 
  echo "---------- Running ${BENCHMARK} eight threads non-prefetch on 8 machines ---------- "
  run 10 8 $NONPREFETCH8 

  cd $TOPDIR
}

benchmarks='rarray rao warray wao'

echo "---------- Clean old files ---------- "
rm ../runlog/*
for b in `echo $benchmarks`
do
  bm=`grep $b bm.txt`
  BENCHMARK=`echo $bm | cut -f1 -d":"`
  ARGS1=`echo $bm | cut -f2 -d":"`
  ARGS2=`echo $bm | cut -f3 -d":"`
  ARGS3=`echo $bm | cut -f4 -d":"`
  ARGS4=`echo $bm | cut -f5 -d":"`
  ARGS5=`echo $bm | cut -f6 -d":"`
  ARGS6=`echo $bm | cut -f7 -d":"`
  ARGS7=`echo $bm | cut -f8 -d":"`
  ARGS8=`echo $bm | cut -f9 -d":"`
  EXTENSION=`echo $bm | cut -f10 -d":"`
  callrun
done

#----------Calulates  the averages ----------- 
for file in `ls ../runlog/*.txt`
do
  echo -n $file >> average.txt
  cat $file | grep -v "^Command" | awk '{sum += $1} END {print " "sum/NR}' >> average.txt
done
echo "===========" >> average.txt
echo "" >> average.txt

echo "done"
