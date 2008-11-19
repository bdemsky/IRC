#!/bin/sh 

#set -x
MACHINELIST='dc-1.calit2.uci.edu dc-2.calit2.uci.edu dc-3.calit2.uci.edu dc-4.calit2.uci.edu dc-5.calit2.uci.edu dc-6.calit2.uci.edu dc-7.calit2.uci.edu dc-8.calit2.uci.edu'

LOGDIR=/home/adash/research/Robust/src/Benchmarks/Prefetch/runlog
TOPDIR=`pwd`

function run {
  i=0;
  DIR=`pwd`
  while [ $i -lt $1 ]; do
    echo "$DIR" > ~/.tmpdir
    echo "bin=$3" > ~/.tmpvars
    ct=0
    MACHINES=''
    for j in $MACHINELIST; do
      if [ $ct -lt $2 ]; then
         MACHINES='$MACHINES $j'
      fi
      let ct=$ct+1
    done

    if [ $2 -eq 2 ]; then 
      arg=$ARGS2
    fi
    if [ $2 -eq 3 ]; then 
      arg=$ARGS3
    fi
    if [ $2 -eq 4 ]; then 
      arg=$ARGS4
    fi
    if [ $2 -eq 5 ]; then 
      arg=$ARGS5
    fi
    if [ $2 -eq 6 ]; then 
      arg=$ARGS6
    fi
    if [ $2 -eq 7 ]; then 
      arg=$ARGS7
    fi
    if [ $2 -eq 8 ]; then 
      arg=$ARGS8
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
  PREFETCH=${BENCHMARK}N.bin
  NONPREFETCH=${BENCHMARK}NP.bin
  NONPREFETCH_NONCACHE=${BENCHMARK}NPNC.bin

  cd $BMDIR 

  echo "---------- Running local $BMDIR non-prefetch on 1 machine ---------- "
  localrun 10

  echo "---------- Running single thread remote $BMDIR non-prefetch + non-cache on 2 machines ---------- "
#  oneremote 1 1 $NONPREFETCH_NONCACHE
  echo "---------- Running single thread remote $BMDIR non-prefetch on 2 machines ---------- "
#  oneremote 1 1 $NONPREFETCH
  echo "---------- Running single thread remote $BMDIR prefetch on 2 machines ---------- "
#  oneremote 1 1 $PREFETCH


for count in 2 3 4 5 6 7 8
do
echo "------- Running $count threads $BMDIR non-prefetch + non-cache on $count machines -----"
run 10 $count $NONREFETCH_NONCACHE
echo "------- Running $count threads $BMDIR prefetch on $count machines -----"
run 10 $count $PREFETCH
done

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

#benchmarks='em3dver10000100015'
#benchmarks='sorverD'
#benchmarks='10242dconv'
benchmarks='1152fft2d 1600fft2d 40962dconv 20482dconv mmver600 moldynverA'

echo "---------- Clean old files ---------- "
#rm runlog/*
for b in `echo $benchmarks`
do
  bm=`grep $b bm.txt`
  BENCHMARK=`echo $bm | cut -f1 -d":"`
  BMDIR=`echo $bm | cut -f2 -d":"`
  ARGS1=`echo $bm | cut -f3 -d":"`
  ARGS2=`echo $bm | cut -f4 -d":"`
  ARGS3=`echo $bm | cut -f5 -d":"`
  ARGS4=`echo $bm | cut -f6 -d":"`
  ARGS5=`echo $bm | cut -f7 -d":"`
  ARGS6=`echo $bm | cut -f8 -d":"`
  ARGS7=`echo $bm | cut -f9 -d":"`
  ARGS8=`echo $bm | cut -f10 -d":"`
  EXTENSION=`echo $bm | cut -f11 -d":"`
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
  cat $file | grep -v "^Command" | awk '{sum += $1} END {print " "sum/NR}' >> average.txt
done
echo "===========" >> average.txt
echo "" >> average.txt

echo "done"
