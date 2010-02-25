# !/bin/sh

# Usage
#
# ./runjava.sh <num_machine>
#

#set -x

BASEDIR=`pwd`
RECOVERYDIR='recovery'
JAVASINGLEDIR='java'
WAITTIME=200
KILLDELAY=20
LOGDIR=~/research/Robust/src/Benchmarks/Recovery/runlog
MACHINELIST='dc-1.calit2.uci.edu dc-2.calit2.uci.edu dc-3.calit2.uci.edu dc-4.calit2.uci.edu dc-5.calit2.uci.edu dc-6.calit2.uci.edu dc-7.calit2.uci.edu dc-8.calit2.uci.edu'
USER='adash'

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
    ssh dc-${i} pkill -u ${USER} -f ${fileName} 
    i=`expr $i + 1`
  done
}

# killonemachine <Benchmark file name> <machine_num>
function killonemachine {
  fileName=$1
  let "machine= $2";
  echo "killing dc-$machine ${fileName}";
  ssh dc-${machine} pkill -u ${USER} -f ${fileName}
}

# runmachines <log filename>
function runMachines {
  echo "Running on ${NUM_MACHINE} machines ... "
  
  # Start machines
  echo "Running machines"
  let "k= $NUM_MACHINE"
  
  DIR=`echo ${BASEDIR}\/${BM_DIR}\/${RECOVERYDIR}`;
  echo "DIR = $DIR";
 
  # Run machines
  while [ $k -gt 1 ]; do
    echo "SSH into dc-${k}"
    ssh dc-${k} 'cd '$DIR'; ./'$BM_NAME'.bin '>> $1'-'$k 2>&1 &
    k=`expr $k - 1`
    sleep 1
  done
  echo "Running master machine ... "
  echo "ssh dc-1 cd $DIR'; ./$BM_NAME.bin master $NUM_MACHINE $BM_ARGS";
  ssh dc-1 'cd '$DIR'; ./'$BM_NAME'.bin master '$NUM_MACHINE $BM_ARGS >> $1'-1' 2>&1 &
}

########### Normal execution
function runNormalTest {
# Run java version
  j=1;
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

  killclients $fName 8
  sleep 10
  cd -
}

########### Failure case
function runFailureTest {
# Run java version
  j=1;
  BM_DIR=${BM_NAME}
  fName="$BM_NAME.bin";
  cd ${BM_DIR}

  test_iter=1;

  for k in ${ORDER[@]}
  do
    if [ $k -eq 0 ]; then         # if k = 0, it is a new test
      if [ $test_iter -ne 1 ]; then
        sleep $WAITTIME           # wait the end of execution
        killclients $fName 8   # kill alive machines
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
      
      let "delay= $RANDOM % $KILLDELAY + 3"
      sleep $delay
    fi 
  done

  killclients $fName 8   # kill alive machines
  sleep 10
 cd -
}

function runDSM {
  i=0;
  DIR=`pwd`
  HOSTNAME=`hostname`
  while [ $i -lt $1 ]; do
    echo "$DIR" > ~/.tmpdir
    echo "bin=$3" > ~/.tmpvars
    ct=0
    MACHINES=''
    for j in $MACHINELIST; do
      if [ $ct -lt $2 ]; then
        if [ "$j" != "$HOSTNAME" ]; then
          MACHINES="$MACHINES $j"
        fi
      fi
      let ct=$ct+1
    done

    rm dstm.conf
    DSTMDIR=${HOME}/research/Robust/src/Benchmarks/Prefetch/config
    if [ $2 -eq 2 ]; then 
      arg=$ARGS2
      ln -s ${DSTMDIR}/dstm_2.conf dstm.conf
    fi
    if [ $2 -eq 3 ]; then 
      arg=$ARGS3
      ln -s ${DSTMDIR}/dstm_3.conf dstm.conf
    fi
    if [ $2 -eq 4 ]; then 
      arg=$ARGS4
      ln -s ${DSTMDIR}/dstm_4.conf dstm.conf
    fi
    if [ $2 -eq 5 ]; then 
      arg=$ARGS5
      ln -s ${DSTMDIR}/dstm_5.conf dstm.conf
    fi
    if [ $2 -eq 6 ]; then 
      arg=$ARGS6
      ln -s ${DSTMDIR}/dstm_6.conf dstm.conf
    fi
    if [ $2 -eq 7 ]; then 
      arg=$ARGS7
      ln -s ${DSTMDIR}/dstm_7.conf dstm.conf
    fi
    if [ $2 -eq 8 ]; then 
      arg=$ARGS8
      ln -s ${DSTMDIR}/dstm_8.conf dstm.conf
    fi
    chmod +x ~/.tmpvars
    for machine in `echo $MACHINES`
    do
      ssh ${machine} 'cd `cat ~/.tmpdir`; source ~/.tmpvars; ./$bin' &
      echo ""
    done
    sleep 2
    perl -x${TOPDIR} ${TOPDIR}/switch/fetch_stat.pl clear_stats settings=switch/clearsettings.txt
    /usr/bin/time -f "%e" ./$3 master $arg 2> ${LOGDIR}/tmp
    perl -x${TOPDIR} ${TOPDIR}/switch/fetch_stat.pl settings=switch/settings.txt
    cat ${LOGDIR}/tmp >> ${LOGDIR}/${3}_${2}Thrd_${EXTENSION}.txt
    if [ $i -eq 0 ];then echo "<h3> Benchmark=${3} Thread=${2} Extension=${EXTENSION}</h3><br>" > ${LOGDIR}/${3}_${EXTENSION}_${2}Thrd_a.html  ;fi
    cat ${LOGDIR}/tmp >> ${LOGDIR}/${3}_${EXTENSION}_${2}Thrd_a.html
    echo "<a href=\"${3}_${2}Thrd_${EXTENSION}_${i}.html\">Network Stats</a><br>" >> ${LOGDIR}/${3}_${EXTENSION}_${2}Thrd_a.html
    mv ${TOPDIR}/html/dell.html ${LOGDIR}/${3}_${2}Thrd_${EXTENSION}_${i}.html
    echo "Terminating ... "
    for machine in `echo $MACHINES`
    do
      ssh ${machine} 'source ~/.tmpvars; killall $bin'
    done
    sleep 2
    i=`expr $i + 1`
  done
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

  fileName=${BM_NAME}.bin

  # terminate if it doesn't have parameter
  let "NUM_MACHINE= $nummachines + 0";
#
#  echo "====================================== Normal Test =============================="
#  runNormalTest $NUM_MACHINES 1 
#  echo "================================================================================"
#
#  echo "====================================== Failure Test ============================="
#  runFailureTest $NUM_MACHINES
#  echo "================================================================================="
#
#  echo "====================================== Recovery Execution Time ============================="
#  for count in 2 4 6 8
#  do
#     echo "------- Running $count threads $BMDIR non-prefetch + non-cache on $count machines -----"
#     runRecovery 1 $count $NONPREFETCH_NONCACHE
#  done
#  echo "================================================================================="
#
#  echo "====================================== Normal DSM Execution Time ============================="
#  for count in 2 4 6 8
#  do
#  echo "------- Running $count threads $BMDIR non-prefetch + non-cache on $count machines -----"
#  runDSM 1 $count $NONPREFETCH_NONCACHE
#  done
#  echo "================================================================================="
#
  echo "=============== Running javasingle for ${BM_NAME} on 1 machines ================="
  javasingle 1 ${BM_NAME}
  cd $TOPDIR
  echo "================================================================================="

  echo "=============== Running recoverysingle for ${BM_NAME} on 1 machines ================="
  recoverysingle 1 ${BM_NAME}
  cd $TOPDIR
  echo "================================================================================="


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
  DSTMDIR=${HOME}/research/Robust/src/Benchmarks/Prefetch/config
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

echo "---------- Starting Benchmarks ----------"
nmach=$1
source bm_args.txt
echo "----------- done ------------"
