#!/bin/sh
source ~/.tmpvars
name=`cat ~/.tmpvars | cut -f2 -d"="`
args=`cat ~/.tmpparams`
echo $name $args >> /tmp/client_stats.txt
binpid=`ps aux | grep $bin | grep -v grep | grep -v time | awk '{print $2}'`
kill -USR1 $binpid
