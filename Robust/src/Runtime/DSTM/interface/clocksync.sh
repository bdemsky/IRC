#!/bin/bash

gcc -Wall -o server clocksyncserver.c
gcc -Wall -o client clocksyncclient.c

SERVER="dc-1.calit2.uci.edu"
CLIENT="dc-2.calit2.uci.edu dc-3.calit2.uci.edu dc-4.calit2.uci.edu dc-5.calit2.uci.edu dc-6.calit2.uci.edu dc-7.calit2.uci.edu dc-8.calit2.uci.edu"
BASEDIR=~/research/Robust/src/Runtime/DSTM/interface

j=2
for i in $CLIENT; do
  echo $i
  echo "$BASEDIR" > ~/.tmpdir
  echo "./client $j" > ~/.tmpvars
  ssh $i 'cd `cat ~/.tmpdir`; `cat ~/.tmpvars`' &
  ./server
  sleep 2
  j=`expr $j + 1`
done
