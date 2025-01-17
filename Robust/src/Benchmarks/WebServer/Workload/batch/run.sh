#!/bin/bash

let i=0; #Keeps count of number of times the server is launched

rm output #Errors and total number of run count are redirected to this file

#Sets the BRISTLECONE parameter with a certain instruction count, probability and 
#number of failures that can be injected
export BRISTLECONE="-initializerandom -injectinstructionfailures 10 0.00001667 1 -debugtask"
rm -rf results
mkdir results
cd results
while [ $i -le 299 ]; # The number of runs 
do
	../trans.bin & #Launch server executable in background
	sleep 2;
	../Workload/workloaderror 127.0.0.1 2>/dev/null & #Run the first workload
	echo $i >> output;
	sleep 5;
	process=`ps | grep workloaderror | grep -v grep | awk '{print $1}'`
	kill -9 $process #Kill the first workload 
	if [ $? -eq 0 ] #Launch the second worload only if the Failure is injected in the first workload
	then
		../Workload/workloadtrans 127.0.0.1 2>/dev/null
	else
		echo "No Fault Injected" >> output
	fi;
	let "i+=1";
	ps | grep trans | grep -v grep | awk '{print $1}' | xargs kill -9 #Kill the server 
	sleep 1;
done
