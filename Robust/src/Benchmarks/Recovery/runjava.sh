# !/bin/sh
BASEDIR=`pwd`
LOGDIR=${BASEDIR}
DSTM_CONFDIR=${HOME}/research/Robust/src
JAVA_DIR=java
JVM_DIR=jvm
DSM_DIR=dsm
ITERATIONS=2
WAITTIME=60

# killClients <fileName> <# of machines>
function killclients {
  i=1;
  fileName=$1
  while [ $i -le $2 ]; do
    echo "killing dc-$i ${fileName}"
    ssh dc-${i} pkill -u jihoonl -f ${fileName} 
    i=`expr $i + 1`
  done
}

# killonemachine <Benchmark file name> <machine_num>
function killonemachine {
  fileName=$1
  let "machine= $2";
  echo "killing dc-$machine ${fileName}";
  ssh dc-${machine} pkill -u jihoonl -f ${fileName}
}

# runmachines <log filename>
function runMachines {
  echo "Running on ${NUM_MACHINE} machines ... "
  
  # Start machines
  echo "Running machines"
  let "k= $NUM_MACHINE"
  
  echo ${BASEDIR}/${BM_DIR} > ~/.tmpdir
  DIR=`echo ${BASEDIR}\/${BM_DIR}`;
  echo "DIR = $DIR";
  
  # Run machines
  while [ $k -gt 1 ]; do
    echo "SSH into dc-${k}"
    ssh dc-${k} 'cd '$DIR'; ./'$BM_NAME'.bin '>> $1'-'$k &
    k=`expr $k - 1`
    sleep 1
  done
  echo "Running master machine ... "
  echo "ssh dc-1 cd $DIR'; ./$BM_NAME.bin master $NUM_MACHINE $BM_ARGS";
  ssh dc-1 'cd '$DIR'; ./'$BM_NAME'.bin master '$NUM_MACHINE $BM_ARGS >> $1'-1' &
}

########### Normal execution
function runNormalTest {
# Run java version
  j=1;
  BM_DIR=${BM_NAME}
  fileName="$BM_NAME.bin";
  cd ${BM_DIR}

  echo $NUM_MACHINE
  tt=1;
  while [ $tt -le $NUM_MACHINE ]; do
    echo "------------------------------- Normal Test $1 ----------------------------" >> log-$tt
    tt=`expr $tt + 1`
  done
  
# run test
  runMachines log
  
  sleep $WAITTIME

  killclients $fileName 8
  sleep 10
  cd -
}

########### Failure case
function runFailureTest {
# Run java version
  j=1;
  BM_DIR=${BM_NAME}
  fileName="$BM_NAME.bin";
  cd ${BM_DIR}

  tt=1;
  while [ $tt -le $NUM_MACHINE ]; do
    echo "------------------------------- Failure Test $1 ----------------------------" >> log-$tt
    tt=`expr $tt + 1`
  done

  # run all machines
  runMachines log
  sleep 10 # wait until all machine run
  # Kill machines
  for k in 2 4 6 8
  do
   echo "------------------------ dc-$k is killed ------------------------" >> log-$k
   killonemachine $fileName $k
   sleep 10
  done

 sleep $WAITTIME # wait the end of execution
 killclients $fileName 8 # kill alive machines
 sleep 10
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

  fileName=${BM_NAME}.bin
  echo "fileName='$fileName'"

  # terminate if it doesn't have parameter
  let "NUM_MACHINE= $1 + 0";

  if [ $NUM_MACHINE -eq 0 ];
  then
    echo "Wrong input"
    let "Num= 8";
    exit 0
  fi

  echo "BM_NAME= $BM_NAME"
  echo "BM_ARGS= $BM_ARGS"
  echo "NUM_M = $NUM_MACHINE"


  echo "=================================== 1 ================================="
  runNormalTest $NUM_MACHINES 1
  echo "======================================================================="

  t=2;
  while [ $t -le $ITERATIONS ]; do
    echo "==================================== $t ============================="
    runFailureTest $NUM_MACHINES 1
    sleep 10
    echo "====================================================================="
    t=`expr $t + 1`
  done

  killclients $fileName 8

done

echo "----------- done ------------"

