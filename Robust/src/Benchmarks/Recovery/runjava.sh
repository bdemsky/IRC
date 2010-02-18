# !/bin/sh

# Usage
#
# ./runjava.sh <num_machine>
#

BASEDIR=`pwd`
RECOVERYDIR='recovery'
JAVASINGLEDIR='java'
ITERATIONS=1
WAITTIME=30

# 0 mean new test 
# 1~8 machine id to be killed
ORDER=( 0 1 3 5 7 8 2    
        0 1 2 3 4 5 6 
        0 1 8 4 6 3 7 
        0 8 7 3 6 5 4
        0 7 4 6 8 1 2 );

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
  DIR=`echo ${BASEDIR}\/${BM_DIR}\/${RECOVERYDIR}`;
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
    tt=`expr $tt + 1`
  done

  test_iter=0;

  for k in ${ORDER[@]}
  do
    if [ $k -eq 0 ]; then         # if k = 0, it is a new test
      if [ $test_iter -ne 1 ]; then
        sleep $WAITTIME           # wait the end of execution
        killclients $fileName 8   # kill alive machines
      else
        test_iter=`expr $test_iter + 1`
      fi

      echo "------------------------------- Failure Test $test_iter ----------------------------" >> log-$tt
      runMachines log   
      sleep 10           # wait until all machine run

    else                 # if k != 0, time to kill machines!
      echo "------------------------ dc-$k is killed ------------------------" >> log-$k
      killonemachine $fileName $k
      
      let "killdelay= $RANDOM % 2 + 3"
      sleep $killdelay
    fi 
  done

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

  # terminate if it doesn't have parameter
  let "NUM_MACHINE= $1 + 0";

  if [ $NUM_MACHINE -eq 0 ]
  then
    echo "Wrong input"
    echo "Usage : ./runjava.sh <num_machine> <number of machine to be killed>"
    exit 0
  fi

  echo "BM_NAME= $BM_NAME"
  echo "BM_ARGS= $BM_ARGS"
  echo "NUM_M = $NUM_MACHINE"

  echo "====================================== Normal Test =============================="
  runNormalTest $NUM_MACHINES 1 

  echo "================================================================================="
  echo "====================================== Failure Test ============================="
  runFailureTest $NUM_MACHINES
  echo "================================================================================="

  killclients $fileName 8


echo "----------- done ------------"

