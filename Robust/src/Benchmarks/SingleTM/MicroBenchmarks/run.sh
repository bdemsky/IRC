#!/bin/sh

#high contention 4 threads
ARGS4="-t 4 -size 5 -l 10000 -l1 100 -l2 50000"
#high contention 8 threads
ARGS8="-t 8 -size 10 -l 100000 -l1 100 -l2 50000"
for file in `ls STATS*`
do
  num=`echo $file | tr -d ".bin" | tr -d "STATSSingleObjectMod"`
  echo $num
#  /usr/bin/time -f "%e" -o /tmp/time ./$file $ARGS4 -o > /tmp/out
  /usr/bin/time -f "%e" -o /tmp/time ./$file $ARGS8 -o > /tmp/out
  t=`cat /tmp/time`
  nTcommit=`grep TransCommit /tmp/out | awk '{print $3}'`
  nTabort=`grep TransAbort /tmp/out | awk '{print $3}'`
  nSftabort=`grep 'nSoftAbort ' /tmp/out | awk '{print $3}'`
  p=`echo "$nTabort $nSftabort $nTcommit" | awk '{print (($1+$2)/$3)*100}'`
  echo "$num $t $p $file" >> data.file
done
