#!/bin/bash
let i=0
while [ $i -le 100 ];
do
./$1 &> $i.log &
sleep 1
java NetsClient 127.0.0.1 8000 2 50 8 1
killall -SIGUSR2 $1
sleep 1
killall -9 $1
let "i+=1"
done