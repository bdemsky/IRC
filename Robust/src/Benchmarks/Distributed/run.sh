#!/bin/sh -x
LOGDIR=~/research/Robust/src/Benchmarks/Distributed/runlog
function run {
  i=0;
  DIR=`pwd`
  NEWDIR=${DIR}/$2
  while [ $i -lt $1 ]; do
    echo "$DIR" > ~/.tmpdir
    if [ "$4" != "java" ]; then
      echo "java LookUpClient -nObjs 160 -numTrans 1000 -probRead 96 -nLookUp 10" > `cat ~/.runbm`
    else
      echo "./Client.bin -nObjs 160 -numTrans 1000 -probRead 96 -nLookUp 10" > `cat ~/.runbm`
    fi

    j=1;
    while [ $j -le $NUM_MACHINES ]; do
      #Start the server
      #cd $2
      cd ${DIR}/$2
      /usr/bin/time -f "%e" $3 -N $j -nObjs 160 2> ${LOGDIR}/server_${3}_${j}_${4}.txt &
      # Start the clients
      k=0;
      while [ $k -lt $j ]; do
        cli=`expr $k + 2`
        echo "SSH into dc-${cli}"
        ssh dc-${cli}.calit2.uci.edu 'cd `cat ~/.tmpdir; ~/.runbm' &
        k=`expr $k + 1`
      done
      j=`expr $j + 1`
    done
    i=`expr $i + 1`
  done
}

benchmarks=LookUpService
NUM_MACHINES=2

for b in `echo $benchmarks`
do
  bm=`grep $b bmserverjava.txt`
  JAVA_DIR=`echo $bm | cut -f1 -d":"`
  JAVA_BIN=`echo $bm | cut -f2 -d":"`
  JAVA_ARGS=`echo $bm | cut -f3 -d":"`
  run 1 $JAVA_DIR $JAVA_BIN java

  echo --------Move up to parent directory--------------
  cd ../../

  bm=`grep $b bmserverjvm.txt`
  JVM_DIR=`echo $bm | cut -f1 -d":"`
  JVM_BIN=`echo $bm | cut -f2 -d":"`
  JVM_ARGS=`echo $bm | cut -f3 -d":"`
  run 1 $JVM_DIR $JVM_BIN jvm
done

#BASEDIR=LookUpService
#echo "---------- Running benchmarks ---------- "
#for dir in java jvm
#do
#  cd $BASEDIR/$dir
#  echo '$BASEDIR/$dir'
#  runbm
#  cd -
#done


#----------Calulates  the averages ----------- 
#for file in `ls runlog/*.txt`
#do
#  echo -n $file >> average.txt
#  cat $file | grep -v "^Command" | awk '{sum += $1} END {print " "sum/NR}' >> average.txt
#done
#echo "===========" >> average.txt
#echo "" >> average.txt

#echo "done"
