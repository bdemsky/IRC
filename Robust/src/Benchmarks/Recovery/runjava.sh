# !/bin/sh
BASEDIR=`pwd`
LOGDIR=${BASEDIR}
DSTM_CONFDIR=${HOME}/research/Robust/src
JAVA_DIR=java
JVM_DIR=jvm
DSM_DIR=dsm
ITERATIONS=1

function killclients {
  i=1;
  let "k= $NUM_MACHINE";
  while [ $i -le $k ]; do
    echo "killing dc-$i"
    ssh dc-${i} pkill -u jihoonl -f MatrixMultiply.bin
    i=`expr $i + 1`
  done
}

function runjava {
# Run java version
  echo "Runnning ${BM_NAME}"
  j=1;
  BM_DIR=${BM_NAME}

  cd ${BM_DIR}    

  while [ $j -le $ITERATIONS ]; do
    echo "Running on ${NUM_MACHINE} machines ... "

    # Start machines
    echo "Running machines"
    let "k= $NUM_MACHINE"

    echo ${BASEDIR}/${BM_DIR} > ~/.tmpdir
    DIR=`echo ${BASEDIR}\/${BM_DIR}`;
    echo "DIR = $DIR";

    while [ $k -gt 1 ]; do
      echo "SSH into dc-${k}"
      ssh dc-${k} 'cd '$DIR'; ./'$BM_NAME'.bin' &
      k=`expr $k - 1`
    done
    echo "Running master machine ... "
    ssh dc-1 'cd '$DIR'; ./'$BM_NAME'.bin master '$NUM_MACHINE $BM_ARGS

    sleep 1 ;
    j=`expr $j + 1`
  done
  cd -
}

echo "---------- Starting Benchmarks ----------"
exec < bm_args.txt
while read line
do
  BM_NAME=`echo $line | cut -f1 -d":"`
  BM_ARGS=`echo $line | cut -f2 -d":"`

  # Setup for remote machine
  echo "" > ~/.bmargs
  echo "BM_NAME='$BM_NAME'" > ~/.bmargs
  echo "BM_ARGS='$BM_ARGS'" > ~/.bmargs

  let "NUM_MACHINE= $1 + 0";

  if [ $NUM_MACHINE -eq 0 ];
  then
    echo "Wrong input.. ./runjava.sh <num_machine>"
    exit 0
  fi

  echo "BM_NAME= $BM_NAME"
  echo "BM_ARGS= $BM_ARGS"
  echo "NUM_M = $NUM_MACHINE"
  runjava $NUM_MACHINES
  
  echo "done run"

  killclients

  # Clean up
  rm ~/.bmargs
  rm ~/.tmpdir
done

echo "----------- done ------------"

