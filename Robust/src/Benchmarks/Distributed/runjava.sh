#!/bin/sh 
BASEDIR=`pwd`
LOGDIR=${BASEDIR}
DSTM_CONFDIR=${HOME}/research/Robust/src
JAVA_DIR=java
JVM_DIR=jvm
DSM_DIR=dsm
ITERATIONS=10
TOPDIR=${HOME}/research/Robust/src/Prefetch

function killclients {
  i=1;
  while [ $i -le $1 ]; do
    ssh dc-${i}.calit2.uci.edu 'killall Client.bin;'
    i=`expr $i + 1`
  done
}

function runjava {
  # Run java version
  echo "Running java version"
  j=1;
  BM_DIR=${BM_NAME}/${JAVA_DIR}
  while [ $j -le $ITERATIONS ]; do
   echo "Running on $1 machines ... "
   # Start the server
   cd ${BM_DIR}
   suffix=$SERVER_ARGS | tr -d ' '
   echo "Running Server ... "
   /usr/bin/time -f "%e" ./Server.bin -N $1 $SERVER_ARGS 2>> ${LOGDIR}/server_${1}_${BM_NAME}_java.out &
   # Start the clients
   sleep 2;
   k=1;
   echo ${BASEDIR}/${BM_DIR} > ~/.tmpdir
   while [ $k -le $1 ]; do
     echo "SSH into dc-${k}"
     SEED=`expr $k \* 100`
     echo "SEED='$SEED'" > ~/.seed
     if [ $k -eq $1 ];
     then
       ssh dc-${k}.calit2.uci.edu 'cd `cat ~/.tmpdir`; source ~/.bmargs; ./Client.bin $CLIENT_ARGS -seed `hostname | cut -f2 -d"-" | cut -f1 -d"."`'
     else
       ssh dc-${k}.calit2.uci.edu 'cd `cat ~/.tmpdir`; source ~/.bmargs; ./Client.bin $CLIENT_ARGS -seed  `hostname | cut -f2 -d"-" | cut -f1 -d"."`' &
     fi
     k=`expr $k + 1`
   done
   killclients $k
   sleep 10;
   j=`expr $j + 1`
   cd -
  done
}

function runjvm {
  # Run java version
  echo "Running jvm version"
  j=1;
  BM_DIR=${BM_NAME}/${JVM_DIR}
  while [ $j -le $ITERATIONS ]; do
   echo "Running on $1 machines ... "
   # Start the server
   cd ${BM_DIR}
   suffix=$SERVER_ARGS | tr -d ' '
   echo "Running Server ... "
   /usr/bin/time -f "%e" java $JVM_SERVER_CLASS -N $1 $SERVER_ARGS 2>> ${LOGDIR}/server_${1}_${BM_NAME}_jvm.out &
   # Start the clients
   k=1;
   echo ${BASEDIR}/${BM_DIR} > ~/.tmpdir
   while [ $k -le $1 ]; do
     echo "SSH into dc-${k}"
     seed=`expr $k * 100`
     if [ $k -eq $1 ];
     then
       ssh dc-${k}.calit2.uci.edu 'cd `cat ~/.tmpdir`; source ~/.bmargs; java $JVM_CLIENT_CLASS $CLIENT_ARGS -seed $seed'
     else
       ssh dc-${k}.calit2.uci.edu 'cd `cat ~/.tmpdir`; source ~/.bmargs; java $JVM_CLIENT_CLASS $CLIENT_ARGS -seed $seed' &
     fi
     k=`expr $k + 1`
   done
   sleep 20;
   j=`expr $j + 1`
   cd -
  done
}

function calcavg {
  for file in `ls ${LOGDIR}/*.out`
  do
    echo -n $file
    cat $file | awk '{sum += $1} END {print " "sum/NR}'
  done
}

exec < bm_args.txt
while read line
do
  BM_NAME=`echo $line | cut -f1 -d":"`
  SERVER_ARGS=`echo $line | cut -f2 -d":"`
  CLIENT_ARGS=`echo $line | cut -f3 -d":"`
  JVM_SERVER_CLASS=`echo $line | cut -f4 -d":"`
  JVM_CLIENT_CLASS=`echo $line | cut -f5 -d":"`

  # Setup for remote machine
  echo "" > ~/.bmargs
  echo "BM_NAME='$BM_NAME'"  > ~/.bmargs
  echo "SERVER_ARGS='$SERVER_ARGS'" >> ~/.bmargs
  echo "CLIENT_ARGS='$CLIENT_ARGS'" >> ~/.bmargs
  echo "JVM_SERVER_CLASS='$JVM_SERVER_CLASS'" >> ~/.bmargs
  echo "JVM_CLIENT_CLASS='$JVM_CLIENT_CLASS'" >> ~/.bmargs
  NUM_MACHINES=$1
  runjava $NUM_MACHINES
  #runjvm $NUM_MACHINES
  # Cleanup
  rm ~/.bmargs
  rm ~/.tmpdir
done

echo "------- Calculating Averages -------- "
calcavg 
