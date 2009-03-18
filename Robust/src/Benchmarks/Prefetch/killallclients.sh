#!/bin/sh
source ~/.tmpvars 
binpid=`ps aux | grep $bin | grep -v grep | grep -v time | awk '{print $2}'`
kill -USR1 $binpid
