#!/bin/bash

let i=0; #Keeps count of number of times the server is launched

rm output #Errors and total number of run count are redirected to this file

#Sets the BRISTLECONE parameter with a certain instruction count, probability and 
#number of failures that can be injected
#export BRISTLECONE="-initializerandom -injectinstructionfailures 10 0.00001667 1 -debugtask"
export BRISTLECONE="-initializerandom -injectinstructionfailures 35 0.00001667 50"
rm -rf results
mkdir results
cd results
while [ $i -le 201 ]; # The number of runs 
  do
  mkdir trial$i
  cd trial$i
  let errorcount=0
  let count=0
  ../../trans.bin &> log & #Launch server executable in background
  sleep 2;
  ../../Workload/workload 127.0.0.1 2>/dev/null & #Run the first workload
  echo $i >> ../output;
  while true
  do
  process=`ps | grep wget | grep -v grep | awk '{print $1}'`
  sleep 1
  process2=`ps | grep wget | grep -v grep | awk '{print $1}'`
  if [ "$process" != "" ]
  then
  if [ "$process" = "$process2" ]
    then
      let "count=1"
    kill -9 $process #Kill the first workload 
    if [ $? -eq 0 ] #Launch the second worload only if the Failure is injected in the first workload
    then
      let "errorcount+=1"
    fi
  fi
else
      if [ "$process2" != "" ]
	  then
	  let "count=1"
	  else
	  let "count+=1"
	  if [ $count == 30 ]
	  then
	      break
	  fi
	  fi
  fi
done
echo Errorcount=$errorcount >> ../output
let "i+=1";
process=`ps | grep workload | grep -v grep | awk '{print $1}'`
kill -9 $process
ps | grep trans | grep -v grep | awk '{print $1}' | xargs kill #Kill the server 
sleep 1;
cd ..
done
