#!/bin/sh 

#set -x
MACHINES2='dw-9.eecs.uci.edu'
MACHINES3='dw-9.eecs.uci.edu dw-7.eecs.uci.edu'
MACHINES4='dw-9.eecs.uci.edu dw-7.eecs.uci.edu dw-5.eecs.uci.edu'
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
    chmod +x ~/.tmpvars
    for machine in `echo $MACHINES`
    do
      ssh ${machine} 'cd `cat ~/.tmpdir`; source ~/.tmpvars; ./$bin' &
      echo ""
    done
    sleep 2
    /usr/bin/time -f "%e" ./$3 master $arg 2>> ${LOGDIR}/${3}_${EXTENSION}.txt
    echo "Terminating ... "
    for machine in `echo $MACHINES`
    do
      ssh ${machine} 'source ~/.tmpvars; killall $bin'
    done
    sleep 2
    i=`expr $i + 1`
  done
}

function oneremote {
  i=0;
  DIR=`pwd` 
  while [ $i -lt $1 ]; do
    echo "$DIR" > ~/.tmpdir
    echo "bin=$3" > ~/.tmpvars
    echo "arg='$ARGS1'" > ~/.tmpargs
    echo "logd=$LOGDIR" > ~/.tmplogdir
    echo "ext=$EXTENSION" > ~/.tmpext
    ./$3 &
    ssh $MACHINES2 'cd `cat ~/.tmpdir`; source ~/.tmpvars; source ~/.tmpargs; source ~/.tmplogdir; source ~/.tmpext; /usr/bin/time -f "%e" ./$bin master $arg 2>> ${logd}/${bin}_remote_${ext}.txt'
    echo "Terminating ... "
    killall $3
    sleep 2
    i=`expr $i + 1`
  done
}

function localrun {
  i=0;
#while [ $i -lt $1 ]; do
#    /usr/bin/time -f "%e" ./${NONPREFETCH} master $ARGS1 2>> ${LOGDIR}/${NONPREFETCH}_local_${EXTENSION}.txt
#   sleep 4
#   i=`expr $i + 1`
# done
  i=0;
  while [ $i -lt $1 ]; do
    /usr/bin/time -f "%e" ./${NONPREFETCH_NONCACHE} master $ARGS1 2>> ${LOGDIR}/${NONPREFETCH_NONCACHE}_local_${EXTENSION}.txt
    sleep 4
    i=`expr $i + 1`
  done
}

function callrun {
  PREFETCH=${BENCHMARK}1.bin
  NONPREFETCH=${BENCHMARK}1NP.bin
  NONPREFETCH_NONCACHE=${BENCHMARK}1NPNC.bin
  PREFETCH2=${BENCHMARK}2.bin
  NONPREFETCH2=${BENCHMARK}2NP.bin
  NONPREFETCH_NONCACHE2=${BENCHMARK}2NPNC.bin
  PREFETCH3=${BENCHMARK}3.bin
  NONPREFETCH3=${BENCHMARK}3NP.bin
  NONPREFETCH_NONCACHE3=${BENCHMARK}3NPNC.bin
  PREFETCH4=${BENCHMARK}4.bin
  NONPREFETCH4=${BENCHMARK}4NP.bin
  NONPREFETCH_NONCACHE4=${BENCHMARK}4NPNC.bin
  cd $BMDIR 

  echo "---------- Running local $BMDIR non-prefetch on 1 machine ---------- "
  localrun 3

  echo "---------- Running single thread remote $BMDIR non-prefetch + non-cache on 2 machines ---------- "
#  oneremote 1 1 $NONPREFETCH_NONCACHE
  echo "---------- Running single thread remote $BMDIR non-prefetch on 2 machines ---------- "
#  oneremote 1 1 $NONPREFETCH
  echo "---------- Running single thread remote $BMDIR prefetch on 2 machines ---------- "
#  oneremote 1 1 $PREFETCH

  echo "---------- Running two threads $BMDIR non-prefetch + non-cache on 2 machines ---------- "
  run 3 2 $NONPREFETCH_NONCACHE2 
  echo "---------- Running two threads $BMDIR non-prefetch on 2 machines ---------- "
#  run 3 2 $NONPREFETCH2 
  echo "---------- Running two threads $BMDIR prefetch on 2 machines ---------- "
  run 3 2 $PREFETCH2 

  echo "---------- Running three threads $BMDIR non-prefetch + non-cache on 3 machines ---------- "
  run 3 3 $NONPREFETCH_NONCACHE3 
  echo "---------- Running three threads $BMDIR non-prefetch on 3 machines ---------- "
#  run 3 3 $NONPREFETCH3 
  echo "---------- Running three threads $BMDIR prefetch on 3 machines ---------- "
  run 3 3 $PREFETCH3 

  echo "---------- Running four threads $BMDIR non-prefetch + non-cache on 4 machines ---------- "
  run 3 4 $NONPREFETCH_NONCACHE4 
  echo "---------- Running four threads $BMDIR non-prefetch on 4 machines ---------- "
#  run 3 4 $NONPREFETCH4 
  echo "---------- Running four threads $BMDIR prefetch on 4 machines ---------- "
  run 3 4 $PREFETCH4 

  cd $TOPDIR
}

function callmicrorun {
  PREFETCH=${BENCHMARK}1.bin
  NONPREFETCH=${BENCHMARK}1NP.bin
  NONPREFETCH_NONCACHE=${BENCHMARK}1NPNC.bin
  cd $BMDIR 
  echo "---------- Running local $BMDIR non-prefetch on 1 machine ---------- "
#  localrun 10
  echo "---------- Running single thread remote $BMDIR non-prefetch + non-cache on 2 machines ---------- "
  oneremote 10 1 $NONPREFETCH_NONCACHE
  echo "---------- Running single thread remote $BMDIR non-prefetch on 2 machines ---------- "
  oneremote 10 1 $NONPREFETCH
  echo "---------- Running single thread remote $BMDIR prefetch on 2 machines ---------- "
  oneremote 10 1 $PREFETCH
  cd $TOPDIR
}

benchmarks='array chase mmver200 mmver600'
#benchmarks='em3dver10000100015'
#benchmarks='moldynverA'
#benchmarks='sorverD' //8000 X 8000 matrix

echo "---------- Clean old files ---------- "
rm runlog/*
for b in `echo $benchmarks`
do
  bm=`grep $b bm.txt`
  BENCHMARK=`echo $bm | cut -f1 -d":"`
  BMDIR=`echo $bm | cut -f2 -d":"`
  ARGS1=`echo $bm | cut -f3 -d":"`
  ARGS2=`echo $bm | cut -f4 -d":"`
  ARGS3=`echo $bm | cut -f5 -d":"`
  ARGS4=`echo $bm | cut -f6 -d":"`
  EXTENSION=`echo $bm | cut -f7 -d":"`
  name1='array'
  name2='chase'
  if [ $b == $name1 ] || [ $b == $name2 ]; then
  callmicrorun
  else
  callrun
  fi
done

#----------Calulates  the averages ----------- 
for file in `ls runlog/*.txt`
do
  echo -n $file >> average.txt
  cat $file | awk '{sum += $1} END {print " "sum/NR}' >> average.txt
done
echo "===========" >> average.txt
echo "" >> average.txt

echo "done"
