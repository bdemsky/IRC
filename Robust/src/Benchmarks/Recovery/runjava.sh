# !/bin/sh

# Usage
#
# ./runjava.sh <num_machine>
#

#set -x

BASEDIR=`pwd`
RECOVERYDIR='recovery'
JAVASINGLEDIR='java'
WAITTIME=120
KILLDELAY=15
LOGDIR=~/research/Robust/src/Benchmarks/Recovery/runlog
DSTMDIR=${HOME}/research/Robust/src/Benchmarks/Prefetch/config
MACHINELIST='dc-1.calit2.uci.edu dc-2.calit2.uci.edu dc-3.calit2.uci.edu dc-4.calit2.uci.edu dc-5.calit2.uci.edu dc-6.calit2.uci.edu dc-7.calit2.uci.edu dc-8.calit2.uci.edu'
USER='adash'

# 0 mean new test 
# 1~8 machine id to be killed

### Sequential Machine failure order ######
ORDER=( 0 1 3 5 7 8 2    
        0 1 2 3 4 5 6 
        0 1 8 4 6 3 7 
        0 8 7 3 6 5 4
        0 7 4 6 8 1 2 );

#ORDER=( 0 1 8 4 6 3 7 );

#
# killClients <fileName> <# of machines>
function killclients {
  k=1;
  fileName=$1
  while [ $k -le $2 ]; do
    echo "killing dc-$k ${fileName}"
    ssh dc-${k} pkill -u ${USER} -f ${fileName} 
    k=`expr $k + 1`
  done
}

# killClientsWith USR1 signal <fileName> <# of machines>
function killclientswithSignal {
  k=1;
  fileName=$1
  while [ $k -le $2 ]; do
    echo "killing dc-$k ${fileName}"
    ssh dc-${k} killall -USR1 ${fileName} 
    k=`expr $k + 1`
  done
}

# killonemachine <Benchmark file name> <machine_num>
function killonemachine {
  fileName=$1
  let "machine= $2";
  echo "killing dc-$machine ${fileName}";
  #ssh dc-${machine} pkill -u ${USER} -f ${fileName}
  ssh dc-${machine} killall -USR1 ${fileName} 
}

# runmachines <log filename>
function runMachines {
  echo "Running on ${NUM_MACHINE} machines ... "
  
  # Start machines
  echo "Running machines"
  let "k= $NUM_MACHINE"
  
  DIR=`echo ${BASEDIR}\/${BM_DIR}\/${RECOVERYDIR}`;
  echo "DIR = $DIR";

  echo $BM_NAME
 
  # Run machines
  while [ $k -gt 1 ]; do
    echo "SSH into dc-${k}"
    ssh dc-${k} 'cd '$DIR'; ./'$BM_NAME'.bin '>> log'-'$k 2>&1 &
    k=`expr $k - 1`
    sleep 1
  done
  sleep 2
  echo "Running master machine ... "
  echo "ssh dc-1 cd $DIR'; ./$BM_NAME.bin master $NUM_MACHINE $BM_ARGS";
  ssh dc-1 'cd '$DIR'; ./'$BM_NAME'.bin master '$NUM_MACHINE $BM_ARGS >> log'-1' 2>&1 &
}

########### Normal execution
#  runNormalTest $NUM_MACHINES 1 
function runNormalTest {
# Run java version
# j=1;
  BM_DIR=${BM_NAME}
  fName="$BM_NAME.bin";
  echo ${BM_DIR}
  cd ${BM_DIR}

  tt=1;
  while [ $tt -le $NUM_MACHINE ]; do
    echo "------------------------------- Normal Test $1 ----------------------------" >> log-$tt
    tt=`expr $tt + 1`
  done
  
# run test
  runMachines log
  
  sleep $WAITTIME
  killclientswithSignal $fName 8
#killclients $fName 8
  sleep 10
  cd -
}

########### Sequential Failure case ##########
function runFailureTest {
# Run java version
# j=1;
  BM_DIR=${BM_NAME}
  fName="$BM_NAME.bin";
  cd ${BM_DIR}

  test_iter=1;

  for k in ${ORDER[@]}
  do
    if [ $k -eq 0 ]; then         # if k = 0, it is a new test
      if [ $test_iter -ne 1 ]; then
        sleep $WAITTIME           # wait the end of execution
#killclients $fName 8   # kill alive machines
        killclientswithSignal $fName 8  #kill machines when there is more than 1 order
        outputIter=0;
        for outputIter in 1 2 3 4 5 6 7 8
        do
          echo "----------------------------------------------------------------------------------" >> log-$outputIter
        done
      fi

      outputIter=0;
      for outputIter in 1 2 3 4 5 6 7 8
      do
        echo "------------------------------- Failure Test $test_iter ----------------------------" >> log-$outputIter
      done
      echo "------------------------------- Failure Test $test_iter ----------------------------"
      runMachines log   
      sleep 10           # wait until all machine run
      test_iter=`expr $test_iter + 1`
    else                 # if k != 0, time to kill machines!
      echo "------------------------ dc-$k is killed ------------------------" >> log-$k
      echo "------------------------ dc-$k is killed ------------------------"
      killonemachine $fName $k
      
      let "delay= $RANDOM % $KILLDELAY + 4"
      sleep $delay
    fi 
  done

  sleep $WAITTIME           # wait the end of execution
#  killclients $fName 8   # kill alive machines
  killclientswithSignal $fName 8 #kill machines when finished processing everything in ORDER{ }
  sleep 10
 cd -
}

####### Single machine failure Case ############
function runSingleFailureTest {
  BM_DIR=${BM_NAME}
  fName="$BM_NAME.bin";
  cd ${BM_DIR}

  test_iter=1;

#ORDER=( 0 1 8 4 6 3 7 );
#SINGLE_ORDER=( 1 8 4 6 3 2 7 5 );
  SINGLE_ORDER=( 8 4 );


  for machinename in ${SINGLE_ORDER[@]}
  do
    outputIter=0;
    for outputIter in 1 2 3 4 5 6 7 8
    do
      echo "------------------------------- Failure Test $test_iter ----------------------------" >> log-$outputIter
    done
    echo "------------------------------- Failure Test $test_iter ----------------------------"
    runMachines log   
    sleep 10           # wait until all machine run
    test_iter=`expr $test_iter + 1`
    echo "------------------------ dc-$machinename is killed ------------------------" >> log-$k
    echo "------------------------ dc-$machinename is killed ------------------------"
    killonemachine $fName $machinename
    sleep $WAITTIME           # wait till the end of execution
    killclientswithSignal $fName 8  #kill rest of the alive machines
# Insert Randowm delay 
      let "delay= $RANDOM % $KILLDELAY + 4"
      sleep $delay
  done
 cd -
}

###
###runRecovery <num iterations> <num machines> <recovery file name>
###
function runRecovery {
  i=1;
  DIR=`echo ${BASEDIR}\/${BM_NAME}\/${RECOVERYDIR}`;
  cd ${DIR}
  fName="$BM_NAME.bin";
  HOSTNAME=`hostname`
  while [ $i -le $1 ]; do
    tt=1;
    while [ $tt -le $2 ]; do
      echo "------------------------------- running Recovery on $2 machines for iter=$i ----------------------------" >> log-$tt
      tt=`expr $tt + 1`
    done

    #select the correct dstm config file
    rm dstm.conf
    if [ $2 -eq 2 ]; then 
      ln -s ${DSTMDIR}/dstm_2.conf dstm.conf
    fi
    if [ $2 -eq 3 ]; then 
      ln -s ${DSTMDIR}/dstm_3.conf dstm.conf
    fi
    if [ $2 -eq 4 ]; then 
      ln -s ${DSTMDIR}/dstm_4.conf dstm.conf
    fi
    if [ $2 -eq 5 ]; then 
      ln -s ${DSTMDIR}/dstm_5.conf dstm.conf
    fi
    if [ $2 -eq 6 ]; then 
      ln -s ${DSTMDIR}/dstm_6.conf dstm.conf
    fi
    if [ $2 -eq 7 ]; then 
      ln -s ${DSTMDIR}/dstm_7.conf dstm.conf
    fi
    if [ $2 -eq 8 ]; then 
      ln -s ${DSTMDIR}/dstm_8.conf dstm.conf
    fi

    #Start machines
    let "k= $2"
    while [ $k -gt 1 ]; do
      echo "SSH into dc-${k}"
      ssh dc-${k} 'cd '$DIR'; ./'$BM_NAME'.bin '>> log'-'$k 2>&1 &
      k=`expr $k - 1`
      sleep 1
    done
    sleep 2
    #Start master
    echo "Running master machine ..."
    ssh dc-1 'cd '$DIR'; ./'$BM_NAME'.bin master '$2 $BM_ARGS >> log'-1' 2>&1 &
    sleep $WAITTIME
    echo "Terminating ... "
    killclientswithSignal $fName $2
    sleep 5
    i=`expr $i + 1`
  done
  cd -
}

###
###runDSM <num iterations> <num machines> <dsm file name>
###
function runDSM {
  i=1;
  DIR=`echo ${BASEDIR}\/${BM_NAME}\/${RECOVERYDIR}`;
  cd ${DIR}
  fName="$BM_DSM.bin";
  HOSTNAME=`hostname`
  while [ $i -le $1 ]; do
    tt=1;
    while [ $tt -le $2 ]; do
      echo "------------------------------- running DSM on $2 machines for iter=$i ----------------------------" >> log-$tt
      tt=`expr $tt + 1`
    done

    #select the correct dstm config file
    rm dstm.conf
    if [ $2 -eq 2 ]; then 
      ln -s ${DSTMDIR}/dstm_2.conf dstm.conf
    fi
    if [ $2 -eq 3 ]; then 
      ln -s ${DSTMDIR}/dstm_3.conf dstm.conf
    fi
    if [ $2 -eq 4 ]; then 
      ln -s ${DSTMDIR}/dstm_4.conf dstm.conf
    fi
    if [ $2 -eq 5 ]; then 
      ln -s ${DSTMDIR}/dstm_5.conf dstm.conf
    fi
    if [ $2 -eq 6 ]; then 
      ln -s ${DSTMDIR}/dstm_6.conf dstm.conf
    fi
    if [ $2 -eq 7 ]; then 
      ln -s ${DSTMDIR}/dstm_7.conf dstm.conf
    fi
    if [ $2 -eq 8 ]; then 
      ln -s ${DSTMDIR}/dstm_8.conf dstm.conf
    fi

    #Start machines
    let "k= $2"
    while [ $k -gt 1 ]; do
      echo "SSH into dc-${k}"
      ssh dc-${k} 'cd '$DIR'; ./'$BM_DSM'.bin '>> log'-'$k 2>&1 &
      k=`expr $k - 1`
      sleep 3
    done
    sleep 6
    #Start master
    echo "Running master machine ..."
    ssh dc-1 'cd '$DIR'; ./'$BM_DSM'.bin master '$2 $BM_ARGS >> log'-1' 2>&1 &
    sleep $WAITTIME
    echo "Terminating ... "
    killclientswithSignal $fName $2
    sleep 5
    i=`expr $i + 1`
  done
  cd -
}

function testcase {
  nummachines=$1
  shift
  line=$@
  BM_NAME=`echo $line | cut -f1 -d":"`
  BM_ARGS=`echo $line | cut -f2 -d":"`


  # Setup for remote machine
  echo "BM_NAME='$BM_NAME'" 
  echo "BM_ARGS='$BM_ARGS'" 
  BM_DSM=${BM_NAME}DSM

  fileName=${BM_NAME}.bin

  # terminate if it doesn't have parameter
  let "NUM_MACHINE= $nummachines + 0";

#  echo "====================================== Normal Test =============================="
#  runNormalTest $NUM_MACHINES 1 
#  echo "================================================================================"

#  echo "====================================== Failure Test ============================="
#  runFailureTest $NUM_MACHINES
#  echo "================================================================================="

  echo "====================================== Single Failure Test ============================="
  runSingleFailureTest $NUM_MACHINES
  echo "================================================================================="

#  echo "=============== Running javasingle for ${BM_NAME} on 1 machines ================="
#  javasingle 1 ${BM_NAME}
#  cd $TOPDIR
#  echo "================================================================================="

# echo "=============== Running recoverysingle for ${BM_NAME} on 1 machines ================="
#  recoverysingle 1 ${BM_NAME}
#  cd $TOPDIR
#  echo "================================================================================="

#  echo "=============== Running dsmsingle for ${BM_NAME} on 1 machines ================="
#  dsmsingle 1 ${BM_DSM}
#  cd $TOPDIR
#  echo "================================================================================="

#  echo "====================================== Recovery Execution Time ============================="
#  for count in 2 4 8
#  do
#    echo "------- Running $count threads $BM_NAME recovery on $count machines -----"
#    runRecovery 1 $count ${BM_NAME}
#  done
#  echo "================================================================================="

#  echo "====================================== DSM Execution Time ============================="
#  for count in 2 4 8
#  do
#    echo "------- Running $count threads $BM_NAME dsm on $count machines -----"
#    runDSM 1 $count $BM_DSM
#  done
#  echo "================================================================================="

}

function javasingle {

  DIR=`echo ${BASEDIR}\/${2}\/${JAVASINGLEDIR}`;
  cd $DIR
  echo ${BM_ARGS}
  i=0;
  echo "ssh dc-1 cd $DIR'; ./${2}.bin 1 ${BM_ARGS}";
  while [ $i -lt $1 ]; do
    /usr/bin/time -f "%e" ./${2}.bin 1 ${BM_ARGS} 2> ${DIR}/tmp
    cat ${DIR}/tmp >> ${LOGDIR}/${2}_javasingle.txt
    sleep 2
    i=`expr $i + 1`
  done
}

function recoverysingle {
  DIR=`echo ${BASEDIR}\/${2}\/${RECOVERYDIR}`;
  cd $DIR
  echo ${BM_ARGS}
  rm dstm.conf
  ln -s ${DSTMDIR}/dstm_1.conf dstm.conf
  cd `pwd`
  i=0;
  fName="${2}.bin";
  while [ $i -lt $1 ]; do
    echo "Running master machine ... "
    echo "ssh dc-1 cd $DIR'; ./${2}.bin master 1 ${BM_ARGS}";
    ssh dc-1 'cd '$DIR'; ./'${2}'.bin master '1 ${BM_ARGS} >> ${LOGDIR}/${2}_recoverysingle.txt 2>&1 &
    echo "Start waiting"
    sleep $WAITTIME
    echo "killing dc-1 ${fName}"
    pkill -u ${USER} -f ${fName} 
    i=`expr $i + 1`
  done
}

function dsmsingle {
  DIR=`echo ${BASEDIR}\/${BM_NAME}\/${RECOVERYDIR}`;
  cd $DIR
  echo ${BM_ARGS}
  rm dstm.conf
  ln -s ${DSTMDIR}/dstm_1.conf dstm.conf
  cd `pwd`
  i=0;
  fName="${2}.bin";
  while [ $i -lt $1 ]; do
    echo "Running master machine ... "
    echo "ssh dc-1 cd $DIR'; ./${2}.bin master 1 ${BM_ARGS}";
    ssh dc-1 'cd '$DIR'; ./'${2}'.bin master '1 ${BM_ARGS} >> ${LOGDIR}/${BM_NAME}_dsmsingle.txt 2>&1 &
    echo "Start waiting"
    sleep $WAITTIME
    echo "killing dc-1 ${fName}"
    pkill -u ${USER} -f ${fName} 
    i=`expr $i + 1`
  done
}

echo "---------- Starting Benchmarks ----------"
nmach=$1
source bm_args.txt
#source bm_args_16threads.txt
echo "----------- done ------------"
