# !/bin/sh
BASEDIR=`pwd`
LOGDIR=${BASEDIR}
DSTM_CONFDIR=${HOME}/research/Robust/src
JAVA_DIR=java
JVM_DIR=jvm
DSM_DIR=dsm
ITERATIONS=1

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
  echo "killing dc-$machine";
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
    ssh dc-${k} 'cd '$DIR'; ./'$BM_NAME'.bin' &
    k=`expr $k - 1`
    sleep 1
  done
  echo "Running master machine ... "
  echo "ssh dc-1 cd $DIR'; ./$BM_NAME.bin master '$NUM_MACHINE $BM_ARGS";
  ssh dc-1 'cd '$DIR'; ./'$BM_NAME'.bin master '$NUM_MACHINE $BM_ARGS 
}

function runMultiMachineTest {
# Run java version
  echo "Runnning ${BM_NAME}"
  j=1;
  BM_DIR=${BM_NAME}
  fileName="$BM_NAME.bin";
  cd ${BM_DIR}

 ########### Normal execution
  runMachines 
  killclients $fileName 8
  sleep 10

 ########### Failure case-1

  # run all machines
#  runMachines 
#  sleep 10 # wait until all machine run
  # Kill machines
#  for k in 2 4 6 8
#    do
#    killonemachine $fileName $k
#    sleep 30
#  done

#  sleep 1000; # wait the end of execution
  killclients $fileName 8 # kill alive machines
  sleep 10;
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
    killclients $fileName $Num
    exit 0
  fi

  echo "BM_NAME= $BM_NAME"
  echo "BM_ARGS= $BM_ARGS"
  echo "NUM_M = $NUM_MACHINE"
  runMultiMachineTest $NUM_MACHINES
  
  echo "done run"
  
  killclients $fileName

  # Clean up
  rm ~/.bmargs
  rm ~/.tmpdir
done

echo "----------- done ------------"

